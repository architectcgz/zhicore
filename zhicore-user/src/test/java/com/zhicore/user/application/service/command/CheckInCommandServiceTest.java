package com.zhicore.user.application.service.command;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.dto.CheckInVO;
import com.zhicore.user.application.port.store.CheckInStore;
import com.zhicore.user.application.service.query.CheckInQueryService;
import com.zhicore.user.domain.model.UserCheckIn;
import com.zhicore.user.domain.model.UserCheckInStats;
import com.zhicore.user.domain.repository.UserCheckInRepository;
import com.zhicore.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInCommandService 测试")
class CheckInCommandServiceTest {

    @Mock
    private UserCheckInRepository checkInRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CheckInStore checkInStore;

    @InjectMocks
    private CheckInCommandService checkInCommandService;

    @Test
    @DisplayName("月度位图查询应通过 store 完成")
    void getMonthlyBitmap_shouldDelegateToStore() {
        YearMonth yearMonth = YearMonth.of(2026, 3);
        when(checkInStore.getMonthlyBitmap(1001L, yearMonth)).thenReturn(21L);

        CheckInQueryService checkInQueryService = new CheckInQueryService(checkInRepository, checkInStore);
        long bitmap = checkInQueryService.getMonthlyCheckInBitmap(1001L, yearMonth);

        assertEquals(21L, bitmap);
    }

    @Test
    @DisplayName("签到前若位图显示今日已签到应直接拒绝")
    void checkIn_shouldRejectWhenBitmapAlreadyMarked() {
        when(userRepository.existsById(1001L)).thenReturn(true);
        when(checkInStore.hasCheckedIn(any(), any())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> checkInCommandService.checkIn(1001L));

        assertEquals(ResultCode.ALREADY_CHECKED_IN.getCode(), exception.getCode());
        verify(checkInRepository, never()).save(any(UserCheckIn.class));
    }

    @Test
    @DisplayName("签到成功后应通过 store 写入位图")
    void checkIn_shouldMarkBitmapViaStore() {
        when(userRepository.existsById(1001L)).thenReturn(true);
        when(checkInStore.hasCheckedIn(any(), any())).thenReturn(false);
        when(checkInRepository.existsByUserIdAndDate(any(), any())).thenReturn(false);
        when(checkInRepository.findStatsByUserId(1001L)).thenReturn(Optional.of(UserCheckInStats.create(1001L)));

        CheckInVO result = checkInCommandService.checkIn(1001L);

        assertEquals(true, result.getCheckedInToday());
        verify(checkInStore).markCheckedIn(any(Long.class), any(LocalDate.class));
        verify(checkInRepository).save(any(UserCheckIn.class));
    }
}
