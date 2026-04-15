package net.burget.slides.entity;

import java.io.Serializable;

/**
 * A single slide in a presentation.
 */
public class Slide implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sid;
    private String className;
    private String text;
    private Presentation presentation;

    public Slide()
    {
        sid = "faaaaa";
        text = "";
        className = "";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Presentation getPresentation() { return presentation; }
    public void setPresentation(Presentation presentation) { this.presentation = presentation; }

    @Override
    public String toString()
    {
        return "---[id=" + id + ", sid=" + sid + ", class=" + className + "]---\n"
                + text + "\n---\n";
    }

    //================================================================================

    public static String numberToSid(long num)
    {
        final int R = 26;
        final int Z = 65;
        final int MIN_LENGTH = 6;
        final int MAX_LENGTH = 12;
        char[] ret = new char[MAX_LENGTH];
        long n = num;
        int lastNA = -1;
        for (int i = MAX_LENGTH - 1; i >= 0; i--)
        {
            long m = n % R;
            ret[i] = (char) (m + Z);
            if (ret[i] != 'A' && lastNA == -1)
                lastNA = i;
            n = n / R;
        }
        if (lastNA == -1)
            return new String(ret).substring(0, MIN_LENGTH).toLowerCase();
        else
            return new String(ret).substring(0, Math.max(lastNA + 1, MIN_LENGTH)).toLowerCase();
    }

    public static long sidToNumber(String sid)
    {
        String csid = sid.toUpperCase();
        final int MAX_LENGTH = 12;
        while (csid.length() < MAX_LENGTH)
            csid = csid + 'A';
        final int R = 26;
        final int Z = 65;
        long r = 1;
        long ret = 0;
        for (int i = csid.length() - 1; i >= 0; i--)
        {
            final char c = csid.charAt(i);
            ret = ret + r * (((int) c) - Z);
            r = r * R;
        }
        return ret;
    }
}
