package com.baiyi.opscloud.facade.impl;

import com.baiyi.opscloud.account.AccountCenter;
import com.baiyi.opscloud.builder.UserPermissionBuilder;
import com.baiyi.opscloud.common.base.AccessLevel;
import com.baiyi.opscloud.common.redis.RedisUtil;
import com.baiyi.opscloud.common.util.*;
import com.baiyi.opscloud.decorator.ServerGroupDecorator;
import com.baiyi.opscloud.decorator.ServerTreeDecorator;
import com.baiyi.opscloud.domain.BusinessWrapper;
import com.baiyi.opscloud.domain.DataTable;
import com.baiyi.opscloud.domain.ErrorEnum;
import com.baiyi.opscloud.domain.generator.opscloud.*;
import com.baiyi.opscloud.domain.param.server.ServerGroupParam;
import com.baiyi.opscloud.domain.param.server.ServerGroupTypeParam;
import com.baiyi.opscloud.domain.param.user.UserServerTreeParam;
import com.baiyi.opscloud.domain.vo.server.OcServerAttributeVO;
import com.baiyi.opscloud.domain.vo.server.OcServerGroupTypeVO;
import com.baiyi.opscloud.domain.vo.server.OcServerGroupVO;
import com.baiyi.opscloud.domain.vo.server.ServerTreeVO;
import com.baiyi.opscloud.domain.vo.tree.TreeVO;
import com.baiyi.opscloud.facade.ServerCacheFacade;
import com.baiyi.opscloud.facade.ServerFacade;
import com.baiyi.opscloud.facade.ServerGroupFacade;
import com.baiyi.opscloud.facade.UserPermissionFacade;
import com.baiyi.opscloud.factory.attribute.impl.AttributeAnsible;
import com.baiyi.opscloud.server.facade.ServerAttributeFacade;
import com.baiyi.opscloud.service.server.OcServerGroupService;
import com.baiyi.opscloud.service.server.OcServerGroupTypeService;
import com.baiyi.opscloud.service.server.OcServerService;
import com.baiyi.opscloud.service.user.OcUserService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author baiyi
 * @Date 2020/2/21 11:00 上午
 * @Version 1.0
 */
@Service
public class ServerGroupFacadeImpl implements ServerGroupFacade {

    @Resource
    private OcServerGroupService ocServerGroupService;

    @Resource
    private OcServerGroupTypeService ocServerGroupTypeService;

    @Resource
    private OcServerService ocServerService;

    @Resource
    private ServerGroupDecorator serverGroupDecorator;

    @Resource
    private UserPermissionFacade userPermissionFacade;

    @Resource
    private AccountCenter accountCenter;

    @Resource
    private OcUserService ocUserService;

    @Resource
    private ServerAttributeFacade serverAttributeFacade;

    @Resource
    private AttributeAnsible attributeAnsible;

    @Resource
    private ServerFacade serverFacade;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private ServerTreeDecorator serverTreeDecorator;

    @Resource
    private ServerCacheFacade serverCacheFacade;


    public static final boolean ACTION_ADD = true;
    public static final boolean ACTION_UPDATE = false;

    @Override
    public DataTable<OcServerGroupVO.ServerGroup> queryServerGroupPage(ServerGroupParam.PageQuery pageQuery) {
        DataTable<OcServerGroup> table = ocServerGroupService.queryOcServerGroupByParam(pageQuery);
        List<OcServerGroupVO.ServerGroup> page = BeanCopierUtils.copyListProperties(table.getData(), OcServerGroupVO.ServerGroup.class);
        DataTable<OcServerGroupVO.ServerGroup> dataTable = new DataTable<>(page.stream().map(e -> serverGroupDecorator.decorator(e)).collect(Collectors.toList()), table.getTotalNum());
        return dataTable;
    }

    @Override
    public BusinessWrapper<Boolean> addServerGroup(OcServerGroupVO.ServerGroup serverGroup) {
        return saveServerGroup(serverGroup, ACTION_ADD);
    }

    @Override
    public BusinessWrapper<Boolean> updateServerGroup(OcServerGroupVO.ServerGroup serverGroup) {
        return saveServerGroup(serverGroup, ACTION_UPDATE);
    }

    private BusinessWrapper<Boolean> saveServerGroup(OcServerGroupVO.ServerGroup serverGroup, boolean action) {
        OcServerGroup checkOcServerGroup = ocServerGroupService.queryOcServerGroupByName(serverGroup.getName());
        if (!RegexUtils.isServerGroupNameRule(serverGroup.getName()))
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_NAME_NON_COMPLIANCE_WITH_RULES);
        OcServerGroup ocServerGroup = BeanCopierUtils.copyProperties(serverGroup, OcServerGroup.class);
        // 对象存在 && 新增
        if (checkOcServerGroup != null && action) {
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_NAME_ALREADY_EXIST);
        }
        if (action) {
            ocServerGroupService.addOcServerGroup(ocServerGroup);
        } else {
            ocServerGroupService.updateOcServerGroup(ocServerGroup);
        }
        serverCacheFacade.evictServerGroupCache(ocServerGroup);
        return BusinessWrapper.SUCCESS;
    }

    @Override
    public BusinessWrapper<Boolean> deleteServerGroupById(int id) {
        OcServerGroup ocServerGroup = ocServerGroupService.queryOcServerGroupById(id);
        if (ocServerGroup == null)
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_NOT_EXIST);
        // 判断server绑定的资源
        int count = ocServerService.countByServerGroupId(id);
        if (count == 0) {
            // 清理缓存
            serverCacheFacade.evictServerGroupCache(ocServerGroup);
            ocServerGroupService.deleteOcServerGroupById(id);
            return BusinessWrapper.SUCCESS;
        } else {
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_HAS_USED);
        }
    }

    @Override
    public DataTable<OcServerGroupTypeVO.ServerGroupType> queryServerGroupTypePage(ServerGroupTypeParam.PageQuery pageQuery) {
        DataTable<OcServerGroupType> table = ocServerGroupTypeService.queryOcServerGroupTypeByParam(pageQuery);
        List<OcServerGroupTypeVO.ServerGroupType> page = BeanCopierUtils.copyListProperties(table.getData(), OcServerGroupTypeVO.ServerGroupType.class);
        DataTable<OcServerGroupTypeVO.ServerGroupType> dataTable = new DataTable<>(page, table.getTotalNum());
        return dataTable;
    }

    @Override
    public BusinessWrapper<Boolean> addServerGroupType(OcServerGroupTypeVO.ServerGroupType serverGroupType) {
        return saveServerGroupType(serverGroupType, ACTION_ADD);
    }

    @Override
    public BusinessWrapper<Boolean> updateServerGroupType(OcServerGroupTypeVO.ServerGroupType serverGroupType) {
        return saveServerGroupType(serverGroupType, ACTION_UPDATE);
    }

    private BusinessWrapper<Boolean> saveServerGroupType(OcServerGroupTypeVO.ServerGroupType serverGroupType, boolean action) {
        OcServerGroupType checkOcServerGroupTypeName = ocServerGroupTypeService.queryOcServerGroupTypeByName(serverGroupType.getName());
        OcServerGroupType ocServerGroupType = BeanCopierUtils.copyProperties(serverGroupType, OcServerGroupType.class);
        // 对象存在 && 新增
        if (checkOcServerGroupTypeName != null && action) {
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_TYPE_NAME_ALREADY_EXIST);
        }
        if (action) {
            ocServerGroupTypeService.addOcServerGroupType(ocServerGroupType);
        } else {
            ocServerGroupTypeService.updateOcServerGroupType(ocServerGroupType);
        }
        return BusinessWrapper.SUCCESS;
    }

    @Override
    public BusinessWrapper<Boolean> deleteServerGroupTypeById(int id) {
        OcServerGroupType ocServerGroupType = ocServerGroupTypeService.queryOcServerGroupTypeById(id);
        if (ocServerGroupType == null)
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_TYPE_NOT_EXIST);
        // 判断默认值
        if (ocServerGroupType.getGrpType() == 0)
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_TYPE_IS_DEFAULT);
        // 判断server绑定的资源
        int count = ocServerGroupService.countByGrpType(ocServerGroupType.getGrpType());
        if (count == 0) {
            ocServerGroupTypeService.deleteOcServerGroupTypeById(id);
            return BusinessWrapper.SUCCESS;
        } else {
            return new BusinessWrapper<>(ErrorEnum.SERVERGROUP_TYPE_HAS_USED);
        }
    }

    @Override
    public DataTable<OcServerGroupVO.ServerGroup> queryUserIncludeServerGroupPage(ServerGroupParam.UserServerGroupPageQuery pageQuery) {
        DataTable<OcServerGroup> table = ocServerGroupService.queryUserIncludeOcServerGroupByParam(pageQuery);
        List<OcServerGroupVO.ServerGroup> page = BeanCopierUtils.copyListProperties(table.getData(), OcServerGroupVO.ServerGroup.class);
        DataTable<OcServerGroupVO.ServerGroup> dataTable = new DataTable<>(page.stream().map(e -> serverGroupDecorator.decorator(e)).collect(Collectors.toList()), table.getTotalNum());
        return dataTable;
    }

    @Override
    public DataTable<OcServerGroupVO.ServerGroup> queryUserExcludeServerGroupPage(ServerGroupParam.UserServerGroupPageQuery pageQuery) {
        DataTable<OcServerGroup> table = ocServerGroupService.queryUserExcludeOcServerGroupByParam(pageQuery);
        List<OcServerGroupVO.ServerGroup> page = BeanCopierUtils.copyListProperties(table.getData(), OcServerGroupVO.ServerGroup.class);
        DataTable<OcServerGroupVO.ServerGroup> dataTable = new DataTable<>(page, table.getTotalNum());
        return dataTable;
    }

    @Override
    public BusinessWrapper<Boolean> grantUserServerGroup(ServerGroupParam.UserServerGroupPermission userServerGroupPermission) {
        OcUserPermission ocUserPermission = UserPermissionBuilder.build(userServerGroupPermission);
        BusinessWrapper<Boolean> wrapper = userPermissionFacade.addOcUserPermission(ocUserPermission);
        if (!wrapper.isSuccess())
            return wrapper;
        try {
            OcUser ocUser = ocUserService.queryOcUserById(ocUserPermission.getUserId());
            OcServerGroup ocServerGroup = ocServerGroupService.queryOcServerGroupById(ocUserPermission.getBusinessId());
            accountCenter.grant(ocUser, ocServerGroup.getName());
            return BusinessWrapper.SUCCESS;
        } catch (Exception e) {
        }
        return new BusinessWrapper(ErrorEnum.USER_GRANT_SERVERGROUP_ERROR);
    }

    @Override
    public BusinessWrapper<Boolean> revokeUserServerGroup(ServerGroupParam.UserServerGroupPermission userServerGroupPermission) {
        OcUserPermission ocUserPermission = UserPermissionBuilder.build(userServerGroupPermission);
        BusinessWrapper<Boolean> wrapper = userPermissionFacade.delOcUserPermission(ocUserPermission);
        if (!wrapper.isSuccess())
            return wrapper;
        try {
            OcUser ocUser = ocUserService.queryOcUserById(ocUserPermission.getUserId());
            OcServerGroup ocServerGroup = ocServerGroupService.queryOcServerGroupById(ocUserPermission.getBusinessId());
            accountCenter.revoke(ocUser, ocServerGroup.getName());
            return BusinessWrapper.SUCCESS;
        } catch (Exception e) {
        }
        return new BusinessWrapper(ErrorEnum.USER_REVOKE_SERVERGROUP_ERROR);
    }

    @Override
    public List<OcServerAttributeVO.ServerAttribute> queryServerGroupAttribute(int id) {
        OcServerGroup ocServerGroup = ocServerGroupService.queryOcServerGroupById(id);
        return serverAttributeFacade.queryServerGroupAttribute(ocServerGroup);
    }

    @Override
    public BusinessWrapper<Boolean> saveServerGroupAttribute(OcServerAttributeVO.ServerAttribute serverAttribute) {
        return serverAttributeFacade.saveServerAttribute(serverAttribute);
    }

    @Override
    public ServerTreeVO.MyServerTree queryUserServerTree(UserServerTreeParam.UserServerTreeQuery userServerTreeQuery, OcUser ocUser) {
        // 过滤空服务器组
        int accessLevel = userPermissionFacade.getUserAccessLevel(ocUser);
        if (accessLevel >= AccessLevel.OPS.getLevel())
            userServerTreeQuery.setUserId(0);
        List<OcServerGroup> serverGroupList
                = ocServerGroupService.queryUserPermissionOcServerGroupByParam(userServerTreeQuery).stream()
                .filter(g -> ocServerService.countByServerGroupId(g.getId()) != 0).collect(Collectors.toList());
        // 缓存
        Map<String, String> serverTreeHostPatternMap = Maps.newHashMap();

        List<TreeVO.Tree> treeList = Lists.newArrayList();
        int treeSize = 0;
        for (OcServerGroup ocServerGroup : serverGroupList) {
            Map<String, List<OcServer>> serverGroupMap = attributeAnsible.grouping(ocServerGroup);
            treeSize += getServerGroupMapSize( serverGroupMap );
            // 组装缓存
            assembleServerTreeHostPatternMap(serverTreeHostPatternMap, serverGroupMap);
            treeList.add(serverTreeDecorator.decorator(ocServerGroup, serverGroupMap));
        }
        ServerTreeVO.MyServerTree myServerTree = ServerTreeVO.MyServerTree.builder()
                .userId(ocUser.getId())
                .uuid(UUIDUtils.getUUID())
                .tree(treeList)
                .size(treeSize)
                .build();
        // 缓存1小时
        String key = RedisKeyUtils.getMyServerTreeKey(ocUser.getId(), myServerTree.getUuid());
        redisUtil.set(key, serverTreeHostPatternMap, TimeUtils.hourTime);
        return myServerTree;
    }

    private void assembleServerTreeHostPatternMap(Map<String, String> serverTreeHostPatternMap, Map<String, List<OcServer>> serverGroupMap) {
        for (String key : serverGroupMap.keySet()) {
            for (OcServer ocServer : serverGroupMap.get(key))
                serverTreeHostPatternMap.put(serverFacade.acqServerName(ocServer), serverAttributeFacade.getManageIp(ocServer));
        }
    }

    private int getServerGroupMapSize(Map<String, List<OcServer>> serverGroupMap ){
        int size =0;
        if(serverGroupMap.isEmpty())
            return size;
        for(String key:serverGroupMap.keySet())
            size += serverGroupMap.get(key).size();
        return size;
    }

    @Override
    public  BusinessWrapper<Boolean> getServerTreeHostPatternMap(String uuid, OcUser ocUser) {
        String key = RedisKeyUtils.getMyServerTreeKey(ocUser.getId(), uuid);
        if (!redisUtil.hasKey(key))
            return new BusinessWrapper<>(ErrorEnum.SERVER_TASK_TREE_NOT_EXIST);
        Map<String, String> serverTreeHostPatternMap = (Map<String, String>) redisUtil.get(key);
        BusinessWrapper wrapper = new BusinessWrapper(Boolean.TRUE);
        wrapper.setBody(serverTreeHostPatternMap);
        return wrapper;
    }

}
