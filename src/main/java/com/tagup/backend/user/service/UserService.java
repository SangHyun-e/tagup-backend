package com.tagup.backend.user.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.team.repository.TeamRepository;
import com.tagup.backend.user.dto.UpdateFavoriteTeamRequest;
import com.tagup.backend.user.dto.UserProfileResponse;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User user) {
        User found = userRepository.findByIdWithTeam(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(found);
    }

    @Transactional
    public UserProfileResponse updateFavoriteTeam(User user, UpdateFavoriteTeamRequest request) {
        User found = userRepository.findById(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));

        found.updateFavoriteTeam(team);
        return UserProfileResponse.from(found);
    }
}
