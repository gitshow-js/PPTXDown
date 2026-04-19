/**
 * 
 */
package net.burget.slides.imports;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.poi.xslf.usermodel.XMLSlideShow;

import net.burget.slides.entity.Presentation;
import net.burget.slides.entity.Resource;
import net.burget.slides.entity.Slide;

/**
 * @author burgetr
 *
 */
public class PPTXConvertor
{
    
    public static void outputMarkdown(Presentation pres, String odir) throws IOException
    {
        PrintStream out = new PrintStream(new FileOutputStream(odir + "/content.md"));
        for (Slide slide : pres.getSlides())
        {
            out.println(slide.getText());
            out.println("---");
        }
        out.close();
    }
    
    public static void outputMedia(Presentation pres, File odir) throws IOException
    {
        if (pres.getResources().isEmpty())
            return;
        for (Resource res : pres.getResources())
        {
            OutputStream out = new FileOutputStream(new File(odir, res.getName()));
            out.write(res.getData());
            out.close();
        }
    }

    public static void outputSlides(Presentation pres, File odir) throws IOException
    {
        System.out.println("Outputting slides to " + odir);
        if (pres.getSlides().isEmpty())
            return;
        for (Resource res : pres.getSlideImages())
        {
            OutputStream out = new FileOutputStream(new File(odir, res.getName()));
            out.write(res.getData());
            out.close();
        }
    }

    public static void main(String[] args)
    {
        if (args.length != 2) {
           System.err.println("Usage: PPTXConvertor <input_file> <output_dir>");
           return;
        }
        
        String ifile = args[0];
        String odir = args[1];
        
        PPTXLoader loader = new PPTXLoader();
        loader.setGenerateSlideImages(true);
        // Directory for assets
        File assetsDir = new File(odir, loader.getAssetsDir());
        assetsDir.mkdirs();
        // Directory for entire slides
        File slidesDir = new File(odir, loader.getSlidesDir());
        slidesDir.mkdirs();
        
        try {
            FileInputStream is = new FileInputStream(ifile);
            try (XMLSlideShow ppt = new XMLSlideShow(is)) {
                Presentation pres = loader.createPresentation(ppt);
                outputMarkdown(pres, odir);
                outputMedia(pres, assetsDir);
                outputSlides(pres, slidesDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
