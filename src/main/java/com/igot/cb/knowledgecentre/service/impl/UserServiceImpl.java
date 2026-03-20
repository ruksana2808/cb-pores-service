package com.igot.cb.knowledgecentre.service.impl;
import com.igot.cb.knowledgecentre.service.UserService;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final CassandraOperation cassandraOperation;

    @Override
    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        log.info("UserService::fetchUserFromPrimary: inside");
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_USER, propertyMap,
                Arrays.asList(Constants.FIRST_NAME, Constants.ID), null);
        return userInfoList.stream()
                .map(userInfo -> {
                    Map<String, Object> userMap = new HashMap<>();
                    String userId = (String) userInfo.get(Constants.ID);
                    String userName = (String) userInfo.get(Constants.FIRST_NAME);
                    userMap.put(Constants.USER_ID_KEY, userId);
                    userMap.put(Constants.FIRST_NAME_KEY, userName);
                    return userMap;
                })
                .collect(Collectors.toList());
    }
}