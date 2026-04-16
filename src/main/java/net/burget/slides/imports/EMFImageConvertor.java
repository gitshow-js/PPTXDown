package net.burget.slides.imports;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.DefaultExtensionHandler;
import org.apache.batik.svggen.SVGGraphics2D;
import org.freehep.graphicsio.emf.EMFInputStream;
import org.freehep.graphicsio.emf.EMFRenderer;

import net.burget.slides.entity.Resource;

/**
 * Converts EMF vector graphics to SVG using FreeHEP for EMF parsing
 * and Apache Batik SVGGraphics2D for SVG output.
 */
public class EMFImageConvertor implements ResourceConvertor
{
    @Override
    public String getInputMimeType()
    {
        return "image/x-emf";
    }

    @Override
    public String getOuputMimeType()
    {
        return "image/svg+xml";
    }

    @Override
    public Resource convert(Resource src)
    {
        // Ensure AWT runs without a display in CLI context
        System.setProperty("java.awt.headless", "true");

        try {
            // 1. Parse EMF using FreeHEP
            ByteArrayInputStream istream = new ByteArrayInputStream(src.getData());
            EMFRenderer emfr = new EMFRenderer(new EMFInputStream(istream));
            Dimension size = emfr.getSize();

            // 2. Create an SVG DOM document via Batik
            var domImpl = GenericDOMImplementation.getDOMImplementation();
            var document = domImpl.createDocument(
                    "http://www.w3.org/2000/svg", "svg", null);

            // 3. Create Batik SVGGraphics2D
            //    ImageHandlerBase64Encoder: embeds any raster images as data URIs (self-contained SVG)
            //    textAsShapes=true: converts text to paths, avoids font/headless issues
            SVGGraphics2D svgGenerator = new SVGGraphics2D(document,
                    new JDKBase64ImageHandler(),
                    new DefaultExtensionHandler(),
                    true);

            if (size != null) {
                svgGenerator.setSVGCanvasSize(size);
            }

            // 4. Render EMF onto the Batik recording surface
            emfr.paint(svgGenerator);

            // 5. Stream the SVG DOM to bytes
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            try (Writer writer = new OutputStreamWriter(ostream, StandardCharsets.UTF_8)) {
                svgGenerator.stream(writer, true);
            }

            // 6. Build result Resource
            Resource ret = new Resource();
            ret.setMimeType(getOuputMimeType());
            ret.setTitle(src.getTitle());
            ret.setName(src.getName().replaceAll("\\.emf$", ".svg"));
            ret.setData(ostream.toByteArray());
            ret.setSize(ret.getData().length);
            return ret;

        } catch (IOException e) {
            System.err.println("EMF conversion failed for " + src.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
