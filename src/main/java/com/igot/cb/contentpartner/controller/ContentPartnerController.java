package com.igot.cb.contentpartner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.contentpartner.service.ContentPartnerService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.jclouds.rest.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contentpartner")
public class ContentPartnerController {
    @Autowired
    private ContentPartnerService partnerService;

    @PostMapping("/v1/create")
    public ResponseEntity<ApiResponse> create(@RequestBody JsonNode contentPartnerDetails) {
        return createOrUpdateResponse(contentPartnerDetails);
    }

    @PostMapping("/v1/update")
    public ResponseEntity<ApiResponse> update(@RequestBody JsonNode contentPartnerDetails) {
        return createOrUpdateResponse(contentPartnerDetails);
    }

    private ResponseEntity<ApiResponse> createOrUpdateResponse(JsonNode contentPartnerDetails) {
        ApiResponse response = partnerService.createOrUpdate(contentPartnerDetails);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/v1/read/{id}")
    public ResponseEntity<ApiResponse> read(@PathVariable String id) {
        ApiResponse response = partnerService.read(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    @PostMapping("/v1/search")
    public ResponseEntity<ApiResponse> search(@RequestBody SearchCriteria searchCriteria) {
        ApiResponse response = partnerService.searchEntity(searchCriteria);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/v1/delete/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable String id) {
        ApiResponse response = partnerService.delete(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/v1/readbypartnercode/{partnercode}")
    public ResponseEntity<ApiResponse> fetchContentDetailsByName(@PathVariable String partnercode) {
        ApiResponse response = partnerService.getContentDetailsByPartnerCode(partnercode);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
