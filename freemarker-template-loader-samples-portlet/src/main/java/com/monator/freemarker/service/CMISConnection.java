package com.monator.freemarker.service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.activation.MimetypesFileTypeMap;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisBaseException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisContentAlreadyExistsException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A convinience class that handles the connection against the CMIS repository stated in the <code>freemarker.properties</code> file.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 * 
 */
public class CMISConnection {

    /** Constant to use for logging. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CMISConnection.class);

    /** Default factory implementation that uses the session parameters when creating the session. */
    private SessionFactory sessionFactory = SessionFactoryImpl.newInstance();

    /** The session that connects to the CMIS repository. */
    private Session session;

    /** Session parameters. */
    private Map<String, String> parameters = new HashMap<String, String>();

    /** Map for storing the FTL mime type. */
    private MimetypesFileTypeMap mimetypesFileTypeMap = new MimetypesFileTypeMap();

    /**
     * Creates the connection to the repository using the specified values.
     * 
     * @param repositoryUserName
     *            the repository user name
     * @param repositoryPassword
     *            the repository password
     * @param repositoryURL
     *            the repository URL
     * @param repositoryId
     *            the repository ID
     */
    public CMISConnection(final String repositoryUserName, final String repositoryPassword, final String repositoryURL,
            final String repositoryId) {

        // Add mimetype mapping for ftl file type.
        mimetypesFileTypeMap.addMimeTypes("text/plain ftl FTL");

        // user credentials
        parameters.put(SessionParameter.USER, repositoryUserName);
        parameters.put(SessionParameter.PASSWORD, repositoryPassword);

        // connection settings
        parameters.put(SessionParameter.ATOMPUB_URL, repositoryURL);
        parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);

        // session locale
        parameters.put(SessionParameter.LOCALE_ISO3166_COUNTRY, "se");
        parameters.put(SessionParameter.LOCALE_ISO639_LANGUAGE, "sv");

        // create session
        session = sessionFactory.createSession(parameters);

        LOGGER.info("--------------------------------------------------------");
        LOGGER.info("Connection started");
        LOGGER.info("--------------------------------------------------------");
    }

    /**
     * Create a new folder.
     * 
     * @param folderName
     *            The folder name
     * @param parentFolderId
     *            The CMIS ID of the parent folder
     */
    public final ObjectId createFolder(final String folderName, final String parentFolderId) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.NAME, folderName);
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        try {
            return session.createFolder(properties, session.getObject(parentFolderId));
        } catch (CmisContentAlreadyExistsException e) {
            CmisObject object = session.getObject(session.createObjectId(parentFolderId));
            if (!(object instanceof Folder)) {
                throw new IllegalArgumentException(parentFolderId + " is not a folder");
            }
            Folder folder = (Folder) object;
            for (CmisObject o : folder.getChildren()) {
                if (o.getName().equals(folderName)) {
                    return session.createObjectId(o.getId());
                }
            }

            return null;
        }
    }

    /**
     * For each folder in the given folder path, creates it if necessary. This implementation checks that the folder exists, and if not
     * creates it.
     * 
     * @param folderPath
     *            Folder structure to create
     * 
     * @return the last folder in the structure
     */
    public final CmisObject createFolderStructure(final String folderPath) {
        String[] folderNames = folderPath.split("/");
        String currentObjectId = session.getObjectByPath("/").getId();
        String currentPath = "/";
        for (String folder : folderNames) {
            currentPath = currentPath + folder + "/";
            CmisObject currentObject = getObjectByPath(currentPath);
            currentObjectId = currentObject != null ? currentObject.getId() : createFolder(folder, currentObjectId).getId();
        }
        return session.getObject(currentObjectId);
    }

    /**
     * Retrieves a folder from the repository.
     * 
     * @param folderPath
     *            The path of the folder to retrieve
     * @return The retrieved folder {@link Folder}
     */
    public final Folder getFolderByPath(final String folderPath) {

        return (Folder) getObjectByPath(folderPath);
    }

    /**
     * Retrieve an object from the repository by Id.
     * 
     * @param nodeId
     *            The object's node identity in the repository
     * @return The requested object
     */
    public final CmisObject getObjectById(final String nodeId) {
        try {
            return session.getObject(nodeId);
        } catch (CmisObjectNotFoundException e) {
            return null;
        }
    }

    /**
     * Retrieve an object from the repository by path.
     * 
     * @param path
     *            The folder path to the object in the repository
     * @return The requested object
     */
    public final CmisObject getObjectByPath(final String path) {
        try {
            return session.getObjectByPath(path);
        } catch (CmisObjectNotFoundException e) {
            return null;
        } catch (CmisInvalidArgumentException e) {
            return null;
        } catch (CmisRuntimeException e) {
            LOGGER.info("getObjectByPath: Path to object is empty");
            return null;
        }
    }

    /**
     * Retrieve a template from a given location.
     * 
     * @param path
     *            The path to the template document
     * @return The template document
     */
    public final Document getTemplate(final String path) {

        try {
            Document template = (Document) session.getObjectByPath(path);
            return template;
        } catch (CmisObjectNotFoundException e) {
            return null;
        }
    }

    /**
     * Creates a document in the repository.
     * 
     * @param folderPath
     *            The path to the folder in which to put the file
     * @param fileContent
     *            The {@link InputStream} for the file content
     * @param mimeType
     *            The mimeType of the file
     * @param fileName
     *            The filename to give to the file in the repository
     * @return The Id of the created document
     */
    public final String createDocumentByFolderPath(final String folderPath, final InputStream fileContent, final String mimeType,
            final String fileName) {

        Folder folder = getFolderByPath(folderPath);

        return createDocumentByFolderId(folder.getId(), fileContent, mimeType, fileName);
    }

    /**
     * Creates a document in the repository.
     * 
     * @param folderId
     *            The Id of the folder in which to put the file
     * @param fileContent
     *            The {@link InputStream} for the file content
     * @param mimeType
     *            The mimeType of the file
     * @param fileName
     *            The filename to give to the file in the repository
     * @return The Id of the created document
     */
    public final String createDocumentByFolderId(final String folderId, final InputStream fileContent, final String mimeType,
            final String fileName) {
        // Insert new document
        ObjectId createdObjectId = null;
        try {
            Folder folder = (Folder) session.getObject(folderId);

            Map<String, String> properties = new HashMap<String, String>();
            properties.put(PropertyIds.NAME, fileName);
            properties.put(PropertyIds.CHECKIN_COMMENT, "");
            properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
            LOGGER.info("CreateDocument: " + mimetypesFileTypeMap.getContentType(mimeType));

            ContentStream contentStream = createContentStream(fileContent, mimeType, fileName);
            createdObjectId = session.createDocument(properties, session.createObjectId(folder.getId()), contentStream,
                    VersioningState.MAJOR, null, null, null);

        } catch (CmisBaseException e) {
            LOGGER.info("CBE: " + e.getErrorContent());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return createdObjectId.toString();
    }

    /**
     * A convenience method for creating a content stream for the file content.
     * 
     * @param fileContent
     *            The input stream to set as content stream
     * @param mimeType
     *            The mimeType of the file
     * @param fileName
     *            The filename to give to the file in the repository
     * @return The content stream for the file content
     */
    public final ContentStream createContentStream(final InputStream fileContent, final String mimeType, final String fileName) {

        ContentStreamImpl csi = new ContentStreamImpl();
        csi.setFileName(fileName);
        csi.setMimeType(mimetypesFileTypeMap.getContentType(mimeType));
        csi.setStream(fileContent);

        return csi;
    }

    /**
     * Delete a folder by path.
     * 
     * @param folderPath
     *            The path to the folder to delete
     */
    public final void deleteFolder(final String folderPath) {
        try {
            Folder folder = getFolderByPath(folderPath);
            folder.deleteTree(true, null, true);

        } catch (CmisBaseException e) {
            LOGGER.info("CBE: " + e.getErrorContent());
            e.printStackTrace();
        }
    }

    /**
     * Delete a document by name.
     * 
     * @param docName
     *            The name of the document to delete
     * @param parentFolderId
     *            The Id to the parent folder of the document
     */
    public final void deleteDocument(final String docName, final String parentFolderId) {
        try {
            Document doc;
            Folder folder = (Folder) getObjectById(parentFolderId);
            for (CmisObject object : folder.getChildren()) {
                if (object.getName().equals(docName)) {
                    doc = (Document) object;
                    doc.delete(false);
                }
            }
        } catch (CmisBaseException e) {
            LOGGER.info("CBE: " + e.getErrorContent());
            e.printStackTrace();
        }
    }

    /**
     * Delete a node.
     * 
     * @param nodeId
     *            The Id of the object to delete
     */
    public final void deleteNode(final String nodeId) {

        try {
            CmisObject object = session.getObject(nodeId);

            if (object.getBaseTypeId().equals(BaseTypeId.CMIS_DOCUMENT)) {
                Document doc = (Document) object;
                doc.delete(false);
            } else {
                Folder folder = (Folder) object;
                folder.deleteTree(true, null, true);
            }

        } catch (CmisBaseException e) {
            LOGGER.info("CBE: " + e.getErrorContent());
            e.printStackTrace();
        }
    }
}
