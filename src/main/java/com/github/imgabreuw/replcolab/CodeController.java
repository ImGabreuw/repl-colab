package com.github.imgabreuw.replcolab;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CodeController {

    private final JavaExecutionService javaExecutionService;

    @PostMapping("/run")
    public String runCode(@RequestBody String code) {
        return javaExecutionService.execute(code);
    }

}
