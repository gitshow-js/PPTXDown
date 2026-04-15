package net.burget.slides.imports;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
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
    private static String INDENT_STRING = "\t";

    private List<ResourceConvertor> resourceConvertors;
    
    
    public PPTXLoader()
    {
        resourceConvertors = new ArrayList<>();
        resourceConvertors.add(new EMFImageConvertor());
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
        //convert slides
        Slide prev = null;
        long cnt = 1;
        for (XSLFSlide slide : ppt.getSlides()) 
        {
            Slide newSlide = convertSlide(slide, p);
            newSlide.setId(cnt++);
            if (prev == null)
                p.addSlide(newSlide);
            else
                p.addAfter(prev, newSlide);
            prev = newSlide;
            slideClasses.add(newSlide.getClassName());
        }
        //generate style template
        p.setStyle(generateStyleTemplate(slideClasses));
        return p;
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
        return "\n![" + shape.getShapeName() + "](" + pData.getFileName() + ")\n";
    }
    
    private String linkPicture(XSLFObjectShape shape)
    {
        XSLFPictureData pData = shape.getPictureData();
        return "\n![" + shape.getShapeName() + "](" + pData.getFileName() + ")\n";
    }
    
    private String linkPicture(Resource res)
    {
        return "\n![" + res.getTitle() + "](" + res.getName() + ")\n";
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
            ret.append(INDENT_STRING);
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
    
    
    public static void main(String args[]) throws IOException, OpenXML4JException {

        PrintStream out = System.out;

        if (args.length == 0) {
           out.println("Input file is required");
           return;
        }
        
        FileInputStream is = new FileInputStream(args[0]);
        try (XMLSlideShow ppt = new XMLSlideShow(is)) {
            PPTXLoader loader = new PPTXLoader();
            //loader.outputMarkdown(ppt, out);
            Presentation pres = loader.createPresentation(ppt);
            out.println(pres.toString());
        }
    }

}
