package com.project.FreeCycle.Service;


import com.project.FreeCycle.Domain.Location;
import com.project.FreeCycle.Domain.User;
import com.project.FreeCycle.Dto.CustomUserDetail;
import com.project.FreeCycle.Repository.OAuth2UserInfo;
import com.project.FreeCycle.Repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;



import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOauth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    private final LocationService locationService;
    private final HttpSession httpSession;
    private final HttpServletResponse httpServletResponse;

    @PostConstruct
    public void init() {
        log.info("CustomOauth2UserService 빈 등록 완료");
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        log.info("loadUser 메서드 호출됨");
        OAuth2User oAuth2User = super.loadUser(userRequest); //회원 프로필 조회

        log.info("userRequest clientRegistration : {}", userRequest.getClientRegistration());
        log.info("oAuth2User : {}", oAuth2User);

        return processOAuth2User(userRequest, oAuth2User);

    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {

        String provider = userRequest.getClientRegistration().getClientName();
        OAuth2UserInfo oAuth2UserInfo = null;

        if(provider.equals("naver")){
            log.info("네이버 로그인");
            oAuth2UserInfo = new NaverUserDetails(oAuth2User.getAttributes());
        }


        String providerId = oAuth2UserInfo.getProviderId();
        String userId = oAuth2UserInfo.getEmail(); // userId에 email을 저장함으로써 향후 비밀번호 찾을 때 이메일을 사용하게 함
        String name = oAuth2UserInfo.getName();
        String nickname = oAuth2UserInfo.getNickname();
        String role = "ROLE_USER";
        String email = oAuth2UserInfo.getEmail();
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByUserId(userId));
        User user;

        /* 만약 처음 로그인 시도 했으면 회원가입이 비밀번호 세팅이 
        * 필요하므로 관련된 로직으로 수정 */
        if (userOptional.isEmpty()) {
            user = new User();
            user.setUserId(userId);
            user.setName(name);
            user.setPassword(null);
            user.setRole(role);
            user.setNickname(nickname);
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setEmail(email);

            userRepository.save(user);
            
            // Location 객체를 생성하여 null 값으로 저장
            Location location = new Location();
            location.setUser(user);
            location.setPostcode(null);
            location.setAddress(null);
            location.setDetailAddress(null);
            
            locationService.LocationSave(location);

            log.info("새로운 사용자 저장: {}", userId);


            // 세션에 사용자 ID 저장
            httpSession.setAttribute("userId", userId);
            try {
                httpServletResponse.sendRedirect("/joinPassword");
            } catch (IOException e) {
                log.error("리다이렉션 실패");
            }
            return new CustomUserDetail(user, oAuth2User.getAttributes());
        } else {
            user = userOptional.get();
            log.info("기존 사용자 로그인: {}", userId);
        }

        return new CustomUserDetail(user, oAuth2User.getAttributes());
    }
}