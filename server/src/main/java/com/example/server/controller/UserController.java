package com.example.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.auth.AuthContext;
import com.example.server.auth.JwtService;
import com.example.server.entity.User;
import com.example.server.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class UserController {

    @Autowired(required = false)
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userMapper == null) {
                throw new RuntimeException("UserMapper 未注入，请检查 @Mapper 注解！");
            }
            if (user.getUsername() == null || user.getUsername().isBlank()
                    || user.getPassword() == null || user.getPassword().isBlank()) {
                result.put("code", 400);
                result.put("msg", "账号和密码不能为空");
                return result;
            }

            QueryWrapper<User> query = new QueryWrapper<>();
            query.eq("username", user.getUsername());
            if (userMapper.selectCount(query) > 0) {
                result.put("code", 400);
                result.put("msg", "该账号已存在");
                return result;
            }

            if (user.getNickname() == null || user.getNickname().isEmpty()) {
                user.setNickname("用户" + System.currentTimeMillis());
            }
            user.setRole("USER");
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userMapper.insert(user);

            result.put("code", 200);
            result.put("msg", "注册成功");
            result.put("data", sanitizeUser(user));
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "后端报错: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User loginUser) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userMapper == null) {
                throw new RuntimeException("UserMapper 未注入");
            }
            if (loginUser.getUsername() == null || loginUser.getPassword() == null) {
                result.put("code", 400);
                result.put("msg", "请输入账号和密码");
                return result;
            }

            QueryWrapper<User> query = new QueryWrapper<>();
            query.eq("username", loginUser.getUsername());
            User dbUser = userMapper.selectOne(query);

            if (dbUser == null || !matchesPassword(loginUser.getPassword(), dbUser)) {
                result.put("code", 401);
                result.put("msg", "账号或密码错误");
                return result;
            }

            String token = jwtService.createToken(dbUser);
            result.put("code", 200);
            result.put("msg", "登录成功");
            result.put("token", token);
            result.put("userInfo", sanitizeUser(dbUser));
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "登录报错: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Map<String, Object> result = new LinkedHashMap<>();
        var principal = AuthContext.get();
        if (principal == null) {
            result.put("code", 401);
            result.put("msg", "未登录");
            return result;
        }
        User dbUser = userMapper.selectById(principal.id());
        if (dbUser == null) {
            result.put("code", 401);
            result.put("msg", "用户不存在");
            return result;
        }
        result.put("code", 200);
        result.put("userInfo", sanitizeUser(dbUser));
        return result;
    }

    private boolean matchesPassword(String rawPassword, User dbUser) {
        String stored = dbUser.getPassword();
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (rawPassword.equals(stored)) {
            dbUser.setPassword(passwordEncoder.encode(rawPassword));
            userMapper.updateById(dbUser);
            return true;
        }
        return false;
    }

    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("id", user.getId());
        safe.put("username", user.getUsername());
        safe.put("nickname", user.getNickname());
        safe.put("avatar", user.getAvatar());
        safe.put("role", user.getRole());
        return safe;
    }
}
