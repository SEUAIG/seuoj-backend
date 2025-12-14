package com.seuoj.seuojbackend;

import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.entity.UserRole;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.mapper.UserRoleMapper;
import com.seuoj.seuojbackend.mapper.UserRoleRelMapper;
import com.seuoj.seuojbackend.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
public class JwtTest {
    @Autowired private UserInfoMapper userInfoMapper;
    @Autowired private UserRoleMapper userRoleMapper;
    @Autowired
    private UserRoleRelMapper userRoleRelMapper;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 插入用户
     */
    @Test
    public void createStudent(){
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("123");
        userInfo.setPassword("123");
        userInfoMapper.insert(userInfo);

        UserRole userRole = new UserRole();
        userRole.setRoleCode("STUDENT");
        userRole.setRoleName("学生");
        userRoleMapper.insert(userRole);

        UserRoleRel userRoleRel = new UserRoleRel();
        userRoleRel.setUserId(userInfo.getId());
        userRoleRel.setRoleId(userRole.getId());
        userRoleRelMapper.insert(userRoleRel);
    }

    /**
     * 获取测试JWT
     */
    @Test
    public void getTestJwt(){
        String uuid = "2000100757311807490";
        String token = jwtUtil.createToken(uuid);
        log.info("测试JWT：{}", token);
    }


}
