/**
 * 
 */
package net.burget.slides.imports;

import net.burget.slides.entity.Resource;

/**
 * A convertor that converts a mime type resource to another mime type
 * @author burgetr
 */
public interface ResourceConvertor
{
    
    public String getInputMimeType();

    public String getOuputMimeType();
    
    public Resource convert(Resource src);
    
}
