package com.kpi.lab2_web;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class Controller {

    @GetMapping("/hello")
    public String sayHello() {

        String Name = "Kostyshyna Alina";
        String Group = "KP-31";

        return "Hello from " + Name + " " + Group;
    }
}