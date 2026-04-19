package net.burget.slides.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A presentation consisting of slides and resources.
 */
public class Presentation implements Serializable
{
    private static final long serialVersionUID = 1L;

    public static final long HSTEP = 8031810176L; // 26^7
    public static final long VSTEP = 11881376L;   // 26^5

    private String title;
    private String style = "";
    private List<Slide> slides = new ArrayList<>();
    private List<Resource> resources = new ArrayList<>();
    private List<Resource> slideImages = new ArrayList<>();

    public Presentation() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public List<Slide> getSlides() { return slides; }
    public void setSlides(List<Slide> slides) { this.slides = slides; }

    public List<Resource> getResources() { return resources; }
    public void setResources(List<Resource> resources) { this.resources = resources; }
    
    public List<Resource> getSlideImages() { return slideImages; }
    public void setSlideImages(List<Resource> slideImages) { this.slideImages = slideImages; }

    public void addSlide(Slide slide)
    {
        slides.add(slide);
        slide.setPresentation(this);
    }

    public void addAfter(Slide prevSlide, Slide newSlide)
    {
        Slide prev = null;
        Slide next = null;
        int ii = 0;
        for (Slide slide : slides)
        {
            if (prev != null)
            {
                next = slide;
                break;
            }
            if (slide.getId() == prevSlide.getId())
                prev = slide;
            ii++;
        }
        if (prev != null)
        {
            if (next == null) // last slide
            {
                long psid = Slide.sidToNumber(prev.getSid());
                newSlide.setSid(Slide.numberToSid(psid + HSTEP));
            }
            else
            {
                long psid = Slide.sidToNumber(prev.getSid());
                long nsid = Slide.sidToNumber(next.getSid());
                newSlide.setSid(Slide.numberToSid((psid + nsid) / 2));
            }
            newSlide.setPresentation(this);
            slides.add(ii, newSlide);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");
        for (Slide slide : slides)
            sb.append(slide.toString());
        for (Resource res : resources)
            sb.append(res.toString()).append("\n");
        return sb.toString();
    }
}
