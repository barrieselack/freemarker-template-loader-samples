package com.monator.freemarker.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import freemarker.cache.TemplateLoader;

/**
 * A {@link TemplateLoader} for loading templates from any repository that supports CMIS.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 * 
 */
public class CMISFreemarkerTemplateLoader implements TemplateLoader {

    /** Path to the folder where your templates lies. */
    private String template_folder_path;

    /** Determines if the folders should be created or not when not existing. */
    private boolean create_folder;

    /** Default template which will be copied to the created folder path if create_folder is true. */
    private Resource default_template;

    /** Factory class for constructing CMIS connections. */
    private CMISConnectionFactory conFactory = new CMISConnectionFactory();

    /** The connection to the repository using CMIS. */
    private CMISConnection con = conFactory.getConnection();

    /** Constant to use for logging. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CMISFreemarkerTemplateLoader.class);

    /**
     * Creates a new CMIS Freemarker template loader which uses the specified values when loading templates.
     * 
     * @param folderPath
     *            folder path to where you store your templates.
     * @param createFolder
     *            if true, the folders in folderPath will be created when not existing.
     * @param defaultTemplate
     *            default template which will be copied to the created folder when createFolder is true.
     */
    public CMISFreemarkerTemplateLoader(final String folderPath, final boolean createFolder, final Resource defaultTemplate) {
        this.template_folder_path = folderPath;
        this.create_folder = createFolder;
        this.default_template = defaultTemplate;
    }

    /**
     * Uses Apache OpenCMIS's API through a convenience class, {@link CMISConnection}, to fetch the template from the repository. Folder
     * path is created if missing, depending on the value of <code>create_folder</code>.
     * 
     * {@inheritDoc}
     */
    public final Object findTemplateSource(final String name) throws IOException {
        Object fmTemplate = null;
        String fullPath = "/" + template_folder_path + "/" + name;
        if (con.getFolderByPath("/" + template_folder_path) == null) {
            if (create_folder) {
                LOGGER.info("No folder with that name exists, creating one automatically");
                con.createFolderStructure(template_folder_path);
                LOGGER.info("Creating file!");
                InputStream is = default_template.getInputStream();
                con.createDocumentByFolderPath("/" + template_folder_path, is, "text/plain", default_template.getFilename());
            } else {
                LOGGER.info("No folder with that name exists! Either create the folder manually"
                        + " or set create_folder to true in freemarker.properties to do it automatically.");
                // If the folder doesn't exist return an empty template.
                return fmTemplate;
            }
        }
        fmTemplate = con.getTemplate(fullPath);

        return fmTemplate;
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#getLastModified(java.lang.Object)
     */
    public final long getLastModified(final Object templateSource) {

        return ((CmisObject) templateSource).getLastModificationDate().getTimeInMillis();
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#getReader(java.lang.Object, java.lang.String)
     */
    public final Reader getReader(Object templateSource, final String encoding) throws IOException {

        return new InputStreamReader(((Document) templateSource).getContentStream().getStream(), encoding);
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#closeTemplateSource(java.lang.Object)
     */
    public final void closeTemplateSource(final Object templateSource) throws IOException {
        // Do nothing
    }

}
