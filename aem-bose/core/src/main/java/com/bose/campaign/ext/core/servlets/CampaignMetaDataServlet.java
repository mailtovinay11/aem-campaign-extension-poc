package com.bose.campaign.ext.core.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*************************************************************************
*
* ADOBE CONFIDENTIAL
* __________________
*
*  Copyright 2014 Adobe Systems Incorporated
*  All Rights Reserved.
*
* NOTICE:  All information contained herein is, and remains
* the property of Adobe Systems Incorporated and its suppliers,
* if any.  The intellectual and technical concepts contained
* herein are proprietary to Adobe Systems Incorporated and its
* suppliers and are protected by trade secret or copyright law.
* Dissemination of this information or reproduction of this material
* is strictly forbidden unless prior written permission is obtained
* from Adobe Systems Incorporated.
**************************************************************************/

/*package com.day.cq.mcm.campaign.servlets; */

import com.adobe.cq.mcm.campaign.Constants;
import com.adobe.cq.mcm.campaign.MetaDataExtender;
import com.adobe.cq.mcm.campaign.NewsletterException;
import com.adobe.cq.mcm.campaign.NewsletterManager;
import com.day.cq.mcm.campaign.CallResults;
import com.day.cq.mcm.campaign.CampaignConnector;
import com.day.cq.mcm.campaign.CampaignCredentials;
import com.day.cq.mcm.campaign.CampaignException;
import com.day.cq.mcm.campaign.ConfigurationException;
import com.day.cq.mcm.campaign.ConnectionException;
import com.day.cq.mcm.campaign.Defs;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.webservicesupport.Configuration;

/**
* Servlet that proxies calls to retrieve Adobe Campaign meta data for a newsletter
* resource.
* Please Note - This override OOTB CampaignMetaDataServlet through service ranking approach
*/


@Component(service=Servlet.class,
property={
		"service.ranking:Integer="+"10000",
        "sling.servlet.methods=" + HttpConstants.METHOD_GET,
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.resourceTypes="+ "mcm/campaign/components/newsletter",
        "sling.servlet.resourceTypes="+ "mcm/campaign/components/campaign_newsletterpage",
        "sling.servlet.resourceTypes="+ "mcm/campaign/components/profile",
        "sling.servlet.extensions=" + "json",
        "sling.servlet.selectors="+"campaign.metadata" 
})
@ServiceDescription("Override OOTB Campaign Metadata Servlet for Bose Requirements")


public class CampaignMetaDataServlet extends SlingAllMethodsServlet {

   private final Logger log = LoggerFactory.getLogger(this.getClass());

   @Reference
   private CampaignConnector connector;

   @Reference
   private NewsletterManager manager;

   @Reference(
           policy = ReferencePolicy.DYNAMIC,
           cardinality = ReferenceCardinality.OPTIONAL)
   private volatile MetaDataExtender extender;


   private boolean checkAndProcess(Map<String, String> params,
                                   CampaignCredentials credentials,
                                   HttpServletResponse response,
                                   CampaignConnector connector) throws CampaignException {

       boolean isProxied = false;
       InputStream is = null;
       CallResults callResults = null;

       try {
    	   // Calling Bose specific JSSP
           callResults = connector.callFunction(
                   "amcGetDeliveryMetadataTest.jssp", params, credentials);

           Map<String, String> headers = callResults.getResponseHeaders();
           for (String name : headers.keySet()) {
               // do not copy Content-Length header if the JSON is extended
               if ((this.extender == null) || !name.equalsIgnoreCase("Content-Length")) {
                   response.addHeader(name, headers.get(name));
               }
           }

           if (extender != null) {
               String jsonStr = callResults.bodyAsString();
               JSONObject root = new JSONObject(jsonStr);
               Writer writer = response.getWriter();
               extender.extend(root);
               root.write(writer);
           } else {
               is = callResults.bodyAsStream();
               OutputStream os = response.getOutputStream();
               IOUtils.copy(is, os);
           }
           isProxied = true;

       } catch (ConnectionException ce) {
           if (ce.getStatusCode() != 404) {
               if (ce.getStatusCode() == 500) {
                   throw new CampaignException("Remote error", ce);
               }
               throw new CampaignException("Caught exception while writing response", ce);
           }
       } catch (JSONException je) {
           throw new CampaignException("Caught exception while processing JSON", je);
       } catch (IOException ioe) {
           throw new CampaignException("Caught exception while writing response", ioe);
       } finally {
           IOUtils.closeQuietly(is);
           if (callResults != null) {
               callResults.destroy();
           }
       }
       return isProxied;
   }

   private void proxyTemplate(ValueMap values, CampaignCredentials credentials,
                              SlingHttpServletResponse response) throws CampaignException {

       String templateId = values.get(Defs.PN_TEMPLATE, String.class);
       if (templateId == null) {
           throw new ConfigurationException("Missing template ID on newsletter");
       }

       InputStream is = null;
       CallResults callResults = null;

       Map<String, String> params = new HashMap<String, String>(3);
       params.put(Constants.PRM_DELIVERY, templateId);

       try {

    	   // Calling Bose specific JSSP
           callResults = connector.callFunction(
                   "amcGetDeliveryMetadataTest.jssp", params, credentials);

           Map<String, String> headers = callResults.getResponseHeaders();
           for (String name : headers.keySet()) {
               // do not copy Content-Length header if the JSON is extended
               if ((this.extender == null) || !name.equalsIgnoreCase("Content-Length")) {
                   response.addHeader(name, headers.get(name));
               }
           }

           if (extender != null) {
               String jsonStr = callResults.bodyAsString();
               JSONObject root = new JSONObject(jsonStr);
               Writer writer = response.getWriter();
               extender.extend(root);
               root.write(writer);
           } else {
               is = callResults.bodyAsStream();
               OutputStream os = response.getOutputStream();
               IOUtils.copy(is, os);
           }

       } catch (IOException ioe) {
           throw new CampaignException("Caught exception while writing response", ioe);
       } catch (JSONException je) {
           throw new CampaignException("Caught exception while processing JSON", je);
       } finally {
           IOUtils.closeQuietly(is);
           if (callResults != null) {
               callResults.destroy();
           }
       }
   }

   private void perform(SlingHttpServletRequest request, SlingHttpServletResponse response, boolean doUnlink)
           throws CampaignException {
       Resource resource = request.getResource();
       Page page = resource.getParent().adaptTo(Page.class);
       try {
           if (manager.isNewsletter(page)) {
               performNewsletter(response, page, doUnlink);
           } else {
               performTemplate(response, page);
           }
       } catch (NewsletterException ne) {
           throw new CampaignException("Internal error", ne);
       }
   }

   private void performNewsletter(SlingHttpServletResponse response, Page page, boolean doUnlink)
           throws NewsletterException, CampaignException {
       Resource resource = page.getContentResource();
       ValueMap values = ResourceUtil.getValueMap(resource);
       Configuration config = connector.getWebserviceConfig(resource);
       CampaignCredentials credentials = connector.retrieveCredentials(config);
       String[] links = manager.getLinkedDeliveries(page);
       if (links != null) {
           boolean isProxied = false;
           Map<String, String> params = new HashMap<String, String>(3);
           for (String deliveryId : links) {
               log.debug("Trying delivery '{}'.", deliveryId);
               params.put(Constants.PRM_DELIVERY, deliveryId);
               isProxied = checkAndProcess(params, credentials, response, connector);
               if (isProxied) {
                   log.debug("Success; using meta data of delivery '{}'.", deliveryId);
                   break;
               } else if (doUnlink) {
                   try {
                       manager.unlink(page, deliveryId);
                   } catch (NewsletterException ne) {
                       log.warn("Could not remove link to non-existent delivery '{}'",
                               deliveryId);
                   } catch (PersistenceException pe) {
                       log.warn("Could not remove link to non-existent delivery '{}'",
                               deliveryId);
                   }
               }
           }
           if (!isProxied) {
               log.debug("Using template as no linked delivery matched.");
               this.proxyTemplate(values, credentials, response);
           }
       } else {
           log.debug("Using template; no linked deliveries available.");
           this.proxyTemplate(values, credentials, response);
       }
   }

   private void performTemplate(SlingHttpServletResponse response, Page page)
           throws NewsletterException, CampaignException {
       Resource resource = page.getContentResource();
       ValueMap values = ResourceUtil.getValueMap(resource);
       Configuration config = connector.getWebserviceConfig(resource);
       CampaignCredentials credentials = connector.retrieveCredentials(config);
       this.proxyTemplate(values, credentials, response);
   }

   @Override
   protected final void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
           throws ServletException, IOException {
       try {
           perform(request, response, true);
       } catch (ConfigurationException e) {
           // log info without stacktrace in order not to pollute the log
           log.info("Webservice configuration not found or invalid");
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       } catch (ConnectionException e) {
           log.warn("Could not connect to Adobe Campaign", e);
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       } catch (Exception e) {
           log.info("Internal error while retrieving meta data; see log file.", e);
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       }
   }

   @Override
   protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
           throws ServletException, IOException {
       try {
           perform(request, response, false);
       } catch (ConfigurationException e) {
           // log info without stacktrace in order not to pollute the log
           log.info("Webservice configuration not found or invalid");
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       } catch (ConnectionException e) {
           log.warn("Could not connect to Adobe Campaign", e);
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       } catch (Exception e) {
           log.info("Internal error while proxying data; see log file.", e);
           response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
       }
   }

}