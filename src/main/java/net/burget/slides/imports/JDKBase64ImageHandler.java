package net.burget.slides.imports;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.batik.svggen.ImageHandler;
import org.apache.batik.svggen.SVGGeneratorContext;

/**
 * Batik ImageHandler that encodes images as inline base64 PNG data URIs using
 * javax.imageio.ImageIO, bypassing Batik's SPI-dependent ImageWriterRegistry
 * (which fails to locate writers in a shaded fat JAR).
 */
public class JDKBase64ImageHandler implements ImageHandler
{
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    @Override
    public void handleImage(RenderedImage image, org.w3c.dom.Element imageElement,
                            SVGGeneratorContext generatorContext)
    {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            String dataUri = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(baos.toByteArray());
            imageElement.setAttributeNS(XLINK_NS, "xlink:href", dataUri);
            imageElement.setAttribute("width", String.valueOf(image.getWidth()));
            imageElement.setAttribute("height", String.valueOf(image.getHeight()));
        } catch (IOException e) {
            // Leave the image element empty rather than crashing the whole slide
        }
    }

    @Override
    public void handleImage(Image image, org.w3c.dom.Element imageElement,
                            SVGGeneratorContext generatorContext)
    {
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if (w <= 0 || h <= 0) return;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.createGraphics().drawImage(image, 0, 0, null);
        handleImage((RenderedImage) bi, imageElement, generatorContext);
    }

    @Override
    public void handleImage(RenderableImage image, org.w3c.dom.Element imageElement,
                            SVGGeneratorContext generatorContext)
    {
        handleImage(image.createDefaultRendering(), imageElement, generatorContext);
    }
}
