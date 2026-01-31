package cn.ksuser.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class IndexController {

    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "work");
        return result;
    }
}
