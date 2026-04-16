package net.burget.slides.imports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import net.arnx.wmf2svg.gdi.svg.SvgGdi;
import net.arnx.wmf2svg.gdi.wmf.WmfParser;
import net.burget.slides.entity.Resource;

/**
 * Converts WMF vector graphics to SVG using the wmf2svg library.
 */
public class WMFImageConvertor implements ResourceConvertor
{
    @Override
    public String getInputMimeType()
    {
        return "image/x-wmf";
    }

    @Override
    public String getOuputMimeType()
    {
        return "image/svg+xml";
    }

    @Override
    public Resource convert(Resource src)
    {
        try {
            SvgGdi svgGdi = new SvgGdi(false);
            new WmfParser().parse(new ByteArrayInputStream(src.getData()), svgGdi);

            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            svgGdi.write(ostream);

            Resource ret = new Resource();
            ret.setMimeType(getOuputMimeType());
            ret.setTitle(src.getTitle());
            ret.setName(src.getName().replaceAll("\\.wmf$", ".svg"));
            ret.setData(ostream.toByteArray());
            ret.setSize(ret.getData().length);
            return ret;

        } catch (Exception e) {
            System.err.println("WMF conversion failed for " + src.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
