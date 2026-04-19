package net.burget.slides.imports;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.DefaultExtensionHandler;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFObjectShape;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import net.burget.slides.entity.Presentation;
import net.burget.slides.entity.Resource;
import net.burget.slides.entity.Slide;

/**
 * PPTX import filter
 */
public final class PPTXLoader 
{
    private String assetsDir = "assets";
    private String slidesDir = "slides";
    
    private String indentString = "    ";
    private int minGraphicalShapes = 6;
    private boolean svgTextAsShapes = false;
    private boolean generateSlideImages = true;

    private List<ResourceConvertor> resourceConvertors;
    
    
    public PPTXLoader()
    {
        resourceConvertors = new ArrayList<>();
        resourceConvertors.add(new EMFImageConvertor());
        resourceConvertors.add(new WMFImageConvertor());
    }
    
    public String getAssetsDir()
    {
        return assetsDir;
    }

    public void setAssetsDir(String assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    public String getSlidesDir()
    {
        return slidesDir;
    }

    public void setSlidesDir(String slidesDir)
    {
        this.slidesDir = slidesDir;
    }

    public String getIndentString()
    {
        return indentString;
    }

    public void setIndentString(String indentString)
    {
        this.indentString = indentString;
    }

    public int getMinGraphicalShapes()
    {
        return minGraphicalShapes;
    }

    public void setMinGraphicalShapes(int minGraphicalShapes)
    {
        this.minGraphicalShapes = minGraphicalShapes;
    }

    public boolean isSvgTextAsShapes()
    {
        return svgTextAsShapes;
    }

    public void setSvgTextAsShapes(boolean svgTextAsShapes)
    {
        this.svgTextAsShapes = svgTextAsShapes;
    }

    public boolean isGenerateSlideImages()
    {
        return generateSlideImages;
    }

    public void setGenerateSlideImages(boolean generateSlideImages)
    {
        this.generateSlideImages = generateSlideImages;
    }

    public List<ResourceConvertor> getResourceConvertors()
    {
        return resourceConvertors;
    }

    public void setResourceConvertors(List<ResourceConvertor> resourceConvertors)
    {
        this.resourceConvertors = resourceConvertors;
    }

    public void outputMarkdown(XMLSlideShow ppt, PrintStream out) throws IOException
    {
        for (XSLFSlide slide : ppt.getSlides()) 
        {
            out.println("========");
            out.println(convertSlide(slide, null));
        }
    }
    
    public Presentation createPresentation(XMLSlideShow ppt)
    {
        Presentation p = new Presentation();
        Set<String> slideClasses = new HashSet<>();
        Dimension pageSize = ppt.getPageSize();
        Slide prev = null;
        long cnt = 1;
        for (XSLFSlide slide : ppt.getSlides())
        {
            Slide newSlide = convertSlide(slide, p);
            long slideId = cnt++;
            newSlide.setId(slideId);

            // Render slides that contain graphical shapes (connectors, auto-shapes, tables, etc.)
            // that cannot be represented as text or extracted as picture resources.
            if (hasGraphicalShapes(slide))
            {
                Resource svg = renderGraphicalShapesToSVG(slide, slideId, pageSize);
                if (svg != null)
                {
                    p.getResources().add(svg);
                    newSlide.setText(newSlide.getText()
                            + "\n![Slide " + slideId + "](" + assetsDir + "/" + svg.getName() + ")\n");
                }
            }
            
            if (generateSlideImages)
            {
                Resource img = renderSlideToSVG(slide, slideId, pageSize);
                if (img != null)
                {
                    p.getSlideImages().add(img);
                }
            }

            if (prev == null)
                p.addSlide(newSlide);
            else
                p.addAfter(prev, newSlide);
            prev = newSlide;
            slideClasses.add(newSlide.getClassName());
        }
        p.setStyle(generateStyleTemplate(slideClasses));
        return p;
    }

    /**
     * Returns true if the slide contains any shape that is not a text placeholder,
     * an embedded picture, or a group — i.e. shapes that produce visual content
     * (connectors, auto-shapes used as drawings, tables, etc.) that cannot be
     * captured by text extraction or individual picture resource extraction.
     */
    private boolean hasGraphicalShapes(XSLFSlide slide)
    {
        return countGraphicalShapes(slide) >= minGraphicalShapes;
    }
    
    private int countGraphicalShapes(XSLFSlide slide)
    {
        int count = 0;
        for (XSLFShape shape : slide)
        {
            if (isGraphical(shape))
                count++;
        }
        System.err.println(count + " graphical shapes");
        return count;
    }

    private boolean isGraphical(XSLFShape shape)
    {
        if (shape instanceof XSLFGroupShape)
        {
            for (XSLFShape child : ((XSLFGroupShape) shape).getShapes())
            {
                if (isGraphical(child))
                    return true;
            }
            return false;
        }
        // Pictures and OLE objects are extracted individually; no need to render the slide for them.
        if (shape instanceof XSLFPictureShape) return false;
        if (shape instanceof XSLFObjectShape) return false;
        // IMPORTANT: XSLFAutoShape extends XSLFTextShape, so this check must come first.
        // However, placeholder body/title shapes can also be XSLFAutoShape instances in some
        // PPTX files. Those are already captured as markdown text; exclude them from SVG.
        // Only non-placeholder auto-shapes (drawn shapes, text boxes used as diagram labels,
        // arrows, etc.) carry visual styling that requires SVG rendering.
        if (shape instanceof XSLFAutoShape) {
            Placeholder ph = ((XSLFTextShape) shape).getPlaceholderDetails().getPlaceholder();
            return ph == null;
        }
        // Connector lines/arrows between shapes.
        if (shape instanceof XSLFConnectorShape) return true;
        // Placeholder text shapes (title, body) are handled as text; not graphical.
        if (shape instanceof XSLFTextShape) return false;
        // Any other unrecognised shape type.
        return true;
    }

    private Slide convertSlide(XSLFSlide slide, Presentation pres)
    {
        Slide ret = new Slide();
        String className = slide.getSlideLayout().getName();
        if (className != null)
            ret.setClassName(className.replaceAll("\\W", ""));
        
        StringBuilder text = new StringBuilder();
        
        //find titles
        for (XSLFShape shape : slide)
        {
            if (shape instanceof XSLFTextShape)
            {
                XSLFTextShape txShape = (XSLFTextShape) shape;
                Placeholder type = txShape.getPlaceholderDetails().getPlaceholder(); 
                if (type == Placeholder.TITLE)
                {
                    for (XSLFTextParagraph p : txShape.getTextParagraphs())
                    {
                        //String type = p.isHeaderOrFooter() ? "[hdr]" : "[par]";
                        System.err.println(convertParagraph(p, "# "));
                        text.append(convertParagraph(p, "# "));
                    }
                }
            }
        }
        
        //convert the remaining shapes
        for (XSLFShape shape : slide)
        {
            processShape(shape, text, pres);
        }
        
        ret.setText(text.toString());
        return ret;
    }

    private void processShape(XSLFShape shape, StringBuilder textBuilder, Presentation pres)
    {
        if (shape instanceof XSLFTextShape)
        {
            XSLFTextShape txShape = (XSLFTextShape) shape;
            Placeholder type = txShape.getPlaceholderDetails().getPlaceholder(); 
            //textBuilder.append("<!-- " + type + " -->\n"); //TODO convert only body to text, remaning to graphics? (shape.draw())
            if (type != Placeholder.TITLE)
            {
                for (XSLFTextParagraph p : txShape.getTextParagraphs())
                {
                    //String t = p.isHeaderOrFooter() ? "[hdr]" : "[par]";
                    if (!p.isHeaderOrFooter())
                        textBuilder.append(convertParagraph(p));
                }
            }
        }
        else if (shape instanceof XSLFPictureShape)
        {
            Resource res = createPictureResource(shape.getShapeName(), ((XSLFPictureShape) shape).getPictureData());
            if (res != null)
            {
                textBuilder.append(linkPicture(res));
                if (pres != null)
                    pres.getResources().add(res);
            }
            else
                textBuilder.append(linkPicture((XSLFPictureShape) shape)); //just a link, could not convert contents
        }
        else if (shape instanceof XSLFObjectShape)
        {
            Resource res = createPictureResource(shape.getShapeName(), ((XSLFObjectShape) shape).getPictureData());
            if (res != null)
            {
                textBuilder.append(linkPicture(res));
                if (pres != null)
                    pres.getResources().add(res);
            }
            else
                textBuilder.append(linkPicture((XSLFObjectShape) shape)); //just a link, could not convert contents
        }
        else if (shape instanceof XSLFGroupShape)
        {
            for (XSLFShape ishape : ((XSLFGroupShape) shape).getShapes())
            {
                processShape(ishape, textBuilder, pres);
            }
        }
        else
        {
            System.out.println("Process me: " + shape.getClass());
        }
    }
    
    private String convertParagraph(XSLFTextParagraph p)
    {
        return convertParagraph(p, "");
    }
    
    private String convertParagraph(XSLFTextParagraph p, String prepend)
    {
        StringBuilder ret = new StringBuilder();
        
        indent(p.getIndentLevel(), ret);
        
        if (p.isBullet())
            ret.append("- ");
        else
            ret.append("\n");
        
        ret.append(prepend);
        
        for (XSLFTextRun r : p.getTextRuns())
        {
            formatTextRun(r, ret);
        }
        
        ret.append("\n");
        return ret.toString();
    }
    
    private void formatTextRun(XSLFTextRun r, StringBuilder ret)
    {
        final String s = escapeMD(r.getRawText());
        if (r.isBold() || r.isItalic())
        {
            // find the first and the last non-whitespace char
            int tfirst = -1;
            for (int i = 0; i < s.length() && tfirst == -1; i++)
            {
                if (!Character.isWhitespace(s.charAt(i)))
                    tfirst = i;
            }
            int tlast = -1;
            for (int i = s.length() - 1; i >= 0 && tlast == -1; i--)
            {
                if (!Character.isWhitespace(s.charAt(i)))
                    tlast = i;
            }
            
            if (tfirst != -1) //some non-whitespace chars found
            {
                if (tfirst > 0)
                    ret.append(s.substring(0, tfirst));
                if (r.isBold())
                    ret.append("**");
                if (r.isItalic())
                    ret.append("_");
                ret.append(s.substring(tfirst, tlast + 1));
                if (r.isItalic())
                    ret.append("_");
                if (r.isBold())
                    ret.append("**");
                if (tlast + 1 < s.length())
                    ret.append(s.substring(tlast + 1));
            }
            else
                ret.append(s);
        }
        else
            ret.append(s);
    }
    
    private String linkPicture(XSLFPictureShape shape)
    {
        XSLFPictureData pData = shape.getPictureData();
        return "\n![" + shape.getShapeName() + "](" + assetsDir + "/" + pData.getFileName() + ")\n";
    }

    private String linkPicture(XSLFObjectShape shape)
    {
        XSLFPictureData pData = shape.getPictureData();
        return "\n![" + shape.getShapeName() + "](" + assetsDir + "/" + pData.getFileName() + ")\n";
    }

    private String linkPicture(Resource res)
    {
        return "\n![" + res.getTitle() + "](" + assetsDir + "/" + res.getName() + ")\n";
    }
    
    private Resource createPictureResource(String title, XSLFPictureData data)
    {
        final String type = data.getContentType();

        Resource src = new Resource();
        src.setMimeType(type);
        src.setTitle(title);
        src.setName(data.getFileName());
        src.setData(data.getData());
        src.setSize(src.getData().length);
        
        if (Resource.ALLOWED_MIME_TYPES.contains(type)) //directly supported
        {
            return src;
        }
        else //not supported directly, try convertors
        {
            Resource ret = null;
            for (ResourceConvertor conv : resourceConvertors)
            {
                if (conv.getInputMimeType().equals(type))
                {
                    ret = conv.convert(src);
                    if (ret != null)
                    {
                        System.err.println("Converted " + src.getName() + " to " + ret.getName());
                        break;
                    }
                }
            }
            if (ret == null)
                System.err.println("Skipping unsupported MIME type " + type + " : " + data.getFileName());
            return ret;
        }
    }
    
    private void indent(int level, StringBuilder ret)
    {
        for (int i = 0; i < level; i++)
            ret.append(indentString);
    }
    
    private String escapeMD(String s)
    {
        String ret = s;
        // escape the initial #
        if (ret.startsWith("#")) 
            ret = '\\' + ret;
        // escape HTML tags in text
        ret = ret.replaceAll("<", "\\<"); // TODO ?
        ret = ret.replaceAll(">", "\\>");
        return ret;
    }
    
    private String generateStyleTemplate(Set<String> slideClasses)
    {
        String ret = "";
        for (String cls : slideClasses)
        {
            ret += "slide." + cls + " {\n}\n\n";
        }
        return ret;
    }
    
    /**
     * Renders only the graphical shapes of a slide (auto-shapes, connectors, groups containing
     * such shapes) to SVG using POI's drawing support and Batik's SVGGraphics2D. Title and body
     * placeholder text shapes are excluded — they are already captured as markdown text.
     * Shapes are drawn at their natural slide coordinates; the SVG is cropped to their bounding
     * box via the viewBox attribute so that the title / body area is not included in the image.
     */
    private Resource renderGraphicalShapesToSVG(XSLFSlide slide, long slideNum, Dimension pageSize)
    {
        System.setProperty("java.awt.headless", "true");
        try {
            // Collect graphical shapes and compute their bounding box.
            List<XSLFShape> graphicalShapes = new ArrayList<>();
            Rectangle2D bounds = null;
            for (XSLFShape shape : slide)
            {
                if (isGraphical(shape))
                {
                    graphicalShapes.add(shape);
                    Rectangle2D anchor = shape.getAnchor();
                    bounds = (bounds == null) ? anchor : bounds.createUnion(anchor);
                }
            }

            if (graphicalShapes.isEmpty())
                return null;

            var domImpl = GenericDOMImplementation.getDOMImplementation();
            var document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
            // JDKBase64ImageHandler encodes embedded images via javax.imageio (no Batik SPI needed).
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document,
                    new JDKBase64ImageHandler(),
                    new DefaultExtensionHandler(),
                    svgTextAsShapes);
            // Use the full slide canvas so shapes are drawn at their natural slide coordinates.
            // Rotation centres are therefore computed correctly (no pre-translate to corrupt them).
            svgGenerator.setSVGCanvasSize(pageSize);

            // Draw only the graphical shapes; title/body placeholder shapes are excluded.
            // Each shape must be wrapped with explicit AffineTransform save/restore: POI's
            // DrawFactory.drawShape() (called by shape.draw()) restores the GROUP_TRANSFORM hint
            // but NOT the Graphics2D AffineTransform, so rotations and flips would otherwise
            // accumulate across shapes (cf. DrawSheet.draw() which does its own save/restore).
            for (XSLFShape shape : graphicalShapes)
            {
                AffineTransform savedTransform = svgGenerator.getTransform();
                shape.draw(svgGenerator, null);
                svgGenerator.setTransform(savedTransform);
            }

            // Crop the SVG output to the bounding box of the graphical shapes using viewBox,
            // so the title / body-text area is not visible in the rendered image.
            var svgRoot = svgGenerator.getRoot();
            svgRoot.setAttributeNS(null, "viewBox",
                    bounds.getX() + " " + bounds.getY() + " " +
                    bounds.getWidth() + " " + bounds.getHeight());
            svgRoot.setAttributeNS(null, "width",
                    String.valueOf((int) Math.ceil(bounds.getWidth())));
            svgRoot.setAttributeNS(null, "height",
                    String.valueOf((int) Math.ceil(bounds.getHeight())));

            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            try (Writer writer = new OutputStreamWriter(ostream, StandardCharsets.UTF_8)) {
                svgGenerator.stream(svgRoot, writer, true, false);
            }

            Resource res = new Resource();
            res.setMimeType("image/svg+xml");
            res.setTitle("Slide " + slideNum);
            res.setName("fig_" + slideNum + ".svg");
            res.setData(ostream.toByteArray());
            res.setSize(res.getData().length);
            return res;
        } catch (Exception e) {
            System.err.println("Slide " + slideNum + " rendering failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Renders a full slide to SVG using POI's drawing support and Batik's SVGGraphics2D.
     */
    private Resource renderSlideToSVG(XSLFSlide slide, long slideNum, Dimension pageSize)
    {
        System.setProperty("java.awt.headless", "true");
        try {
            var domImpl = GenericDOMImplementation.getDOMImplementation();
            var document = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
            // JDKBase64ImageHandler encodes embedded images via javax.imageio (no Batik SPI needed).
            // textAsShapes=true avoids font/headless rendering issues.
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document,
                    new JDKBase64ImageHandler(),
                    new DefaultExtensionHandler(),
                    true);
            svgGenerator.setSVGCanvasSize(pageSize);

            slide.draw(svgGenerator);

            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            try (Writer writer = new OutputStreamWriter(ostream, StandardCharsets.UTF_8)) {
                svgGenerator.stream(writer, true);
            }

            Resource res = new Resource();
            res.setMimeType("image/svg+xml");
            res.setTitle("Slide " + slideNum);
            res.setName("slide_" + slideNum + ".svg");
            res.setData(ostream.toByteArray());
            res.setSize(res.getData().length);
            return res;
        } catch (Exception e) {
            System.err.println("Slide " + slideNum + " rendering failed: " + e.getMessage());
            return null;
        }
    }
    
}
