package com.monator.freemarker.controller;

import javax.portlet.RenderResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.bind.annotation.RenderMapping;

/**
 * Controller class for the Liferay Freemarker Template Loader.
 * 
 * @author Andreas Magnusson Monator Technologies AB
 */
@Controller
@RequestMapping("VIEW")
public class LiferayFreemarkerTemplateLoaderController {

    @RenderMapping()
    public String showTemplate(RenderResponse response, Model model) {
        return "view";
    }

}