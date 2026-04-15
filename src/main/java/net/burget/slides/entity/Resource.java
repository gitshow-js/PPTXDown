package net.burget.slides.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * A resource (image, etc.) used in a presentation.
 */
public class Resource
{
    public static final List<String> ALLOWED_MIME_TYPES;
    static {
        ALLOWED_MIME_TYPES = new ArrayList<>(4);
        ALLOWED_MIME_TYPES.add("image/jpeg");
        ALLOWED_MIME_TYPES.add("image/png");
        ALLOWED_MIME_TYPES.add("image/gif");
        ALLOWED_MIME_TYPES.add("image/svg+xml");
    }

    private String name;
    private String title;
    private String mimeType;
    private int size;
    private byte[] data;

    public Resource() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    @Override
    public String toString()
    {
        return "Resource [name=" + name + ", mimeType=" + mimeType + ", size=" + size + "]";
    }
}
