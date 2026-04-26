package com.kpi.lab2_web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/")
public class AuthController {

    @Value("${casdoor.client-id}")
    private String clientId;

    @Value("${casdoor.client-secret}")
    private String clientSecret;

    @Value("${casdoor.redirect-uri}")
    private String redirectUri;

    @Value("${casdoor.url}")
    private String casdoorUrl;

    @GetMapping("/login")
    public RedirectView login() {
        String authUrl = String.format("%s/login/oauth/authorize?client_id=%s&response_type=code&redirect_uri=%s&scope=openid profile email",
                casdoorUrl, clientId, redirectUri);

        return new RedirectView(authUrl);
    }

    private JwtDecoder jwtDecoder() {
        String jwkSetUri = "https://localhost:8443/.well-known/jwks";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo(HttpServletRequest request) {
        String token = null;
        if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                    .filter(c -> "jwt_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Помилка 401: Ви не авторизовані (токен відсутній)");
        }

        try {
            Jwt jwt = jwtDecoder().decode(token);

            Map<String, Object> claims = jwt.getClaims();
            return ResponseEntity.ok(claims);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Помилка 401: Токен недійсний або прострочений");
        }
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, HttpServletResponse httpResponse) throws Exception {

        RestTemplate restTemplate = new RestTemplate();
        String tokenEndpoint = casdoorUrl + "/api/login/oauth/access_token";

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "authorization_code");
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);
        requestBody.add("code", code);
        requestBody.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenEndpoint, request, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.getBody());
            String accessToken = rootNode.path("access_token").asText();

            Cookie cookie = new Cookie("jwt_token", accessToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(604800);

            httpResponse.addCookie(cookie);
            httpResponse.sendRedirect("/");

        } catch (Exception e) {
            httpResponse.getWriter().write("Помилка авторизації: " + e.getMessage());
        }
    }
}