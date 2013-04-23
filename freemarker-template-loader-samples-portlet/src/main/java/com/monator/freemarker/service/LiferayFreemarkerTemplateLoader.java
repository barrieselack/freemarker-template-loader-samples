package com.monator.freemarker.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.cache.MultiVMKeyPoolUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.CompanyConstants;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.GroupConstants;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceContextThreadLocal;
import com.liferay.portlet.documentlibrary.NoSuchFileEntryException;
import com.liferay.portlet.documentlibrary.NoSuchFolderException;
import com.liferay.portlet.documentlibrary.model.DLFileEntry;
import com.liferay.portlet.documentlibrary.model.DLFolder;
import com.liferay.portlet.documentlibrary.service.DLFileEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFolderLocalServiceUtil;
import com.liferay.portlet.dynamicdatamapping.storage.Fields;
import com.monator.freemarker.util.FreemarkerConstants;

import freemarker.cache.TemplateLoader;

/**
 * A {@link TemplateLoader} for loading templates from the Document and Media Library in Liferay.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 * 
 */
public class LiferayFreemarkerTemplateLoader implements TemplateLoader {

    /** Name of the Site under which the template will be found. */
    private String site_name;

    /** Path to the folder where your templates lies. */
    private String template_folder_path;

    /** Determines if the Site should be created or not when not existing. */
    private boolean create_site;

    /** Determines if the folders should be created or not when not existing. */
    private boolean create_folder;

    /** Default template which will be copied to the created folder path if create_folder is true. */
    private Resource default_template;

    /** Constant to use for logging. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LiferayFreemarkerTemplateLoader.class);

    /**
     * Creates a new Liferay Freemarker template loader which uses the specified values when loading templates.
     * 
     * @param siteName
     *            name of the site under which the template will be found.
     * @param createSite
     *            if true, the Site will be created when not existing.
     * @param folderPath
     *            folder path to where you store your templates.
     * @param createFolder
     *            if true, the folders in folderPath will be created when not existing.
     * @param defaultTemplate
     *            default template which will be copied to the created folder when createFolder is true.
     */
    public LiferayFreemarkerTemplateLoader(final String siteName, final boolean createSite, final String folderPath,
            final boolean createFolder, final Resource defaultTemplate) {
        this.site_name = siteName;
        this.create_site = createSite;
        this.template_folder_path = folderPath;
        this.create_folder = createFolder;
        this.default_template = defaultTemplate;
    }

    /**
     * Using Liferay's API to fetch the template from the Document and Media Library and adds the folderId of the template folder and
     * groupId (Site) to Liferay's cache for faster access. Site and folder path is created if missing, depending on the value of
     * <code>create_folder</code> and <code>create_site</code>.
     * 
     * {@inheritDoc}
     */
    public final Object findTemplateSource(final String name) throws IOException {
        Object fmTemplate = null;
        try {
            ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
            long freemarkerTemplateFolderId;
            long groupId = 0;
            Object freemarkerTemplateFolderIdObject = MultiVMKeyPoolUtil.get("freemarkerCache", "freemarkerTemplateFolderId");
            Object groupIdObject = MultiVMKeyPoolUtil.get("freemarkerCache", "freemarkerTemplateGroupId");
            long rootFolderId = CompanyConstants.SYSTEM;

            if (groupIdObject == null) {
                groupId = getTemplatesGroupId(site_name, serviceContext);
                if (groupId == FreemarkerConstants.MISSING_SITE) {
                    // If the Site doesn't exist return an empty template.
                    return fmTemplate;
                }
                MultiVMKeyPoolUtil.put("freemarkerCache", "freemarkerTemplateGroupId", groupId);
            } else {
                groupId = (Long) groupIdObject;
            }

            if (freemarkerTemplateFolderIdObject == null) {
                String[] templateFolders = template_folder_path.split("/");
                List<String> templateFoldersArray = new ArrayList<String>(Arrays.asList(templateFolders));
                freemarkerTemplateFolderId = getTemplateFolderIdFromPath(groupId, rootFolderId, templateFoldersArray, serviceContext);
                if (freemarkerTemplateFolderId == FreemarkerConstants.MISSING_FOLDER) {
                    // If the folder doesn't exist return an empty template.
                    return fmTemplate;
                }
                MultiVMKeyPoolUtil.put("freemarkerCache", "freemarkerTemplateFolderId", freemarkerTemplateFolderId);
            } else {
                freemarkerTemplateFolderId = (Long) freemarkerTemplateFolderIdObject;
            }

            DLFileEntry template = DLFileEntryLocalServiceUtil.getFileEntry(groupId, freemarkerTemplateFolderId, name);
            fmTemplate = template;
        } catch (NoSuchFileEntryException e) {
            LOGGER.debug("No template with name " + name + ", trying next");
        } catch (PortalException e) {
            e.printStackTrace();
        } catch (SystemException e) {
            e.printStackTrace();
        }
        MultiVMKeyPoolUtil.remove("freemarkerCache", "freemarkerTemplateFolderId");
        return fmTemplate;
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#getLastModified(java.lang.Object)
     */
    public final long getLastModified(final Object templateSource) {
        return ((DLFileEntry) templateSource).getModifiedDate().getTime();
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#getReader(java.lang.Object, java.lang.String)
     */
    public final Reader getReader(final Object templateSource, final String encoding) throws IOException {
        Reader templateReader = null;
        try {
            templateReader = new InputStreamReader(((DLFileEntry) templateSource).getContentStream(), encoding);
        } catch (PortalException e) {
            e.printStackTrace();
        } catch (SystemException e) {
            e.printStackTrace();
        }
        return templateReader;
    }

    /* (non-Javadoc)
     * @see freemarker.cache.TemplateLoader#closeTemplateSource(java.lang.Object)
     */
    public void closeTemplateSource(final Object templateSource) throws IOException {
        // Do nothing
    }

    /**
     * Returns the folderId for the last folder in <code>templateFoldersArray</code> or {@link FreemarkerConstants#MISSING_FOLDER} depending
     * on the value of {@link LiferayFreemarkerTemplateLoader#create_folder}.
     * 
     * @param groupId
     *            the Site where the template is located
     * @param folderId
     *            folderId for the parent folder
     * @param templateFoldersArray
     *            array of folder names for the template's folder path
     * @param serviceContext
     *            the service context
     * @return the folderId for the last folder in <code>templateFoldersArray</code> or {@link FreemarkerConstants#MISSING_FOLDER}
     */
    private long getTemplateFolderIdFromPath(final long groupId, long folderId, final List<String> templateFoldersArray,
            final ServiceContext serviceContext) {
        if (templateFoldersArray.size() > 0) {
            DLFolder folder = null;
            try {
                folder = DLFolderLocalServiceUtil.getFolder(groupId, folderId, templateFoldersArray.get(0));
            } catch (NoSuchFolderException e) {
                if (create_folder) {
                    LOGGER.info("No folder with that name exists, creating one automatically");
                    long folderIdFromCounter = 0;
                    try {
                        folderIdFromCounter = CounterLocalServiceUtil.increment();
                        folder = DLFolderLocalServiceUtil.createDLFolder(folderIdFromCounter);
                        folder = DLFolderLocalServiceUtil.addFolder(serviceContext.getUserId(), groupId, groupId, false, folderId,
                                templateFoldersArray.get(0), "", serviceContext);
                        if (templateFoldersArray.size() == 1) {
                            LOGGER.info("Creating file!");
                            Map<String, Fields> fileFields = new HashMap<String, Fields>();
                            InputStream is = default_template.getInputStream();
                            DLFileEntryLocalServiceUtil.addFileEntry(serviceContext.getUserId(), groupId, groupId, folder.getFolderId(),
                                    "view.ftl", MimeTypesUtil.getContentType(default_template.getFile()), "view.ftl", "", "", 0,
                                    fileFields, default_template.getFile(), is, default_template.getFile().length(), serviceContext);
                        }
                    } catch (SystemException e1) {
                        e1.printStackTrace();
                    } catch (PortalException e1) {
                        e1.printStackTrace();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    LOGGER.info("No folder with that name exists! Either create the folder manually"
                            + " or set create_folder to true in freemarker.properties to do it automatically.");
                    return FreemarkerConstants.MISSING_FOLDER;
                }
            } catch (PortalException e) {
                e.printStackTrace();
            } catch (SystemException e) {
                e.printStackTrace();
            }

            folderId = folder.getFolderId();
            templateFoldersArray.remove(0);
            return getTemplateFolderIdFromPath(groupId, folderId, templateFoldersArray, serviceContext);
        }
        return folderId;
    }

    /**
     * Returns the template's groupId or {@link FreemarkerConstants#MISSING_SITE} depending on the value of
     * {@link LiferayFreemarkerTemplateLoader#create_site}.
     * 
     * @param siteName
     *            name of the Site where the template should exist
     * @param serviceContext
     *            the service context
     * @return the groupId of the template or {@link FreemarkerConstants#MISSING_SITE}
     */
    private long getTemplatesGroupId(final String siteName, final ServiceContext serviceContext) {
        long groupId = 0;
        try {
            Group groupToLoadTemplateFrom = GroupLocalServiceUtil.fetchGroup(serviceContext.getCompanyId(), siteName);
            if (Validator.isNull(groupToLoadTemplateFrom) && create_site) {
                LOGGER.info("No Group/Site with that name exists, creating one automatically");
                long groupIdFromCounter = CounterLocalServiceUtil.increment();
                Group newGroup = GroupLocalServiceUtil.createGroup(groupIdFromCounter);
                newGroup = GroupLocalServiceUtil.addGroup(serviceContext.getUserId(), Group.class.getName(), newGroup.getGroupId(),
                        siteName, null, GroupConstants.TYPE_SITE_OPEN, "/" + siteName.toLowerCase(), true, true, serviceContext);
                groupId = newGroup.getGroupId();
            } else if (Validator.isNull(groupToLoadTemplateFrom)) {
                LOGGER.info("No Group/Site with that name exists! Either create the Group/Site manually"
                        + " or set create_site to true in freemarker.properties to do it automatically.");
                return FreemarkerConstants.MISSING_SITE;
            } else {
                groupId = groupToLoadTemplateFrom.getGroupId();
            }
        } catch (SystemException e) {
            e.printStackTrace();
        } catch (PortalException e) {
            e.printStackTrace();
        }
        return groupId;
    }
}
