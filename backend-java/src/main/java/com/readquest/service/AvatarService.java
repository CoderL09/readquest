package com.readquest.service;

import com.readquest.vo.AuthResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {
  AuthResponse.PublicUser replace(String user, MultipartFile file);

  Resource image(String name);
}
