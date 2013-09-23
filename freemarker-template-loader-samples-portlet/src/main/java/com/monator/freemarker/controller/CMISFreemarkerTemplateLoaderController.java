package com.monator.freemarker.controller;

import javax.portlet.RenderResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.bind.annotation.RenderMapping;

/**
 * Controller class for the CMIS Freemarker Template Loader.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 */
@Controller
@RequestMapping("VIEW")
public class CMISFreemarkerTemplateLoaderController {

    @RenderMapping()
    public String showTemplate(RenderResponse response, Model model) {
        return "view";
    }

}