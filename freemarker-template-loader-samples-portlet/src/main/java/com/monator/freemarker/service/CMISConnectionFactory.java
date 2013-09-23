package com.monator.freemarker.service;

import java.io.IOException;
import java.util.Properties;

import org.apache.chemistry.opencmis.commons.exceptions.CmisConnectionException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for creating a connection to the repository using CMIS.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 * 
 */
public class CMISConnectionFactory {

    /** The connection to the repository using CMIS. */
    private CMISConnection con;

    /** Constant to use for logging. */
    private static final Logger LOGGER = LoggerFactory.getLogger(CMISConnectionFactory.class);

    /**
     * Creates a connection to the repository if there isn't already one.
     * 
     * @return the CMIS connection to the repository
     */
    public CMISConnection getConnection() {
        Properties props = new Properties();

        if (con != null) {
            return con;
        } else {
            try {
                props.load(CMISConnectionFactory.class.getClassLoader().getResourceAsStream("freemarker.properties"));
                con = new CMISConnection(props.getProperty("repository.user.name"), props.getProperty("repository.password"),
                        props.getProperty("repository.url"), props.getProperty("repository.id"));
            } catch (CmisConnectionException e) {
                LOGGER.info("Could not create a Cmis connection: " + e.getMessage());
            } catch (CmisUnauthorizedException e) {
                LOGGER.info("Could not create a Cmis connection: " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return con;
        }
    }
}
