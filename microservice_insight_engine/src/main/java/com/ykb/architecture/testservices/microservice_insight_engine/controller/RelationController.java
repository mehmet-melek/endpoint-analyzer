package com.ykb.architecture.testservices.microservice_insight_engine.controller;

import com.ykb.architecture.testservices.microservice_insight_engine.service.ApiRelationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;


@Controller
@RequestMapping("/relations")
public class RelationController
{
    private  ApiRelationService apiRelationService;

    public RelationController(ApiRelationService apiRelationService) {
        this.apiRelationService = apiRelationService;
    }
}
