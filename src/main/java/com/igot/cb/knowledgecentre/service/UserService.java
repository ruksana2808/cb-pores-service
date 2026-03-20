package com.igot.cb.knowledgecentre.service;

import java.util.List;

public interface UserService {
    List<Object> fetchUserFromPrimary(List<String> userIds);

}