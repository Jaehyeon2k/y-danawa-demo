package yju.danawa.com.web;

import java.util.List;

public record LoginResponse(String username, String status, String token, List<String> roles) {
}
