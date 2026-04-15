/**
 * 
 */
package net.burget.slides.imports;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.freehep.graphicsbase.util.export.ExportFileType;
import org.freehep.graphicsio.ImageConstants;
import org.freehep.graphicsio.emf.EMFInputStream;
import org.freehep.graphicsio.emf.EMFRenderer;
import org.freehep.graphicsio.svg.SVGGraphics2D;

import net.burget.slides.entity.Resource;

/**
 * @author burgetr
 *
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
        Resource ret = null;
        byte[] srcdata = src.getData();
        
        try {
            ByteArrayInputStream istream = new ByteArrayInputStream(srcdata);
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            
            List<?> exportFileTypes = ExportFileType.getExportFileTypes(ImageConstants.SVG);
            if (exportFileTypes.size() > 0)
            {
                EMFRenderer emfr = new EMFRenderer(new EMFInputStream(istream));
                SVGGraphics2D svgg = new SVGGraphics2D(ostream, emfr.getSize());
                svgg.startExport();
                emfr.paint(svgg);
                svgg.endExport();
                
                ret = new Resource();
                ret.setMimeType(getOuputMimeType());
                ret.setTitle(src.getTitle());
                ret.setName(src.getName().replaceAll("\\.emf", ".svg"));
                ret.setData(ostream.toByteArray());
                ret.setSize(ret.getData().length);
            }
            else
                System.err.println("No export file type available");
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ret = null;
        }
        return ret;
    }

}
