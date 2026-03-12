package com.yihecode.camera.ai.web;

import com.yihecode.camera.ai.entity.Location;
import com.yihecode.camera.ai.service.LocationService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.JsonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock
    private LocationService locationService;

    @Mock
    private RoleAccessService roleAccessService;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private LocationController locationController;

    @BeforeEach
    void setUp() {
        lenient().when(roleAccessService.canWriteSystem(any())).thenReturn(true);
    }

    @Test
    void saveShouldFailWhenPermissionDenied() throws Exception {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);
        Location location = new Location();
        location.setName("Area-A");

        JsonResult result = locationController.save(location);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(locationService, never()).saveNode(any());
    }

    @Test
    void deleteShouldFailWhenPermissionDenied() {
        when(roleAccessService.canWriteSystem(any())).thenReturn(false);

        JsonResult result = locationController.delete(1L);

        assertEquals(500, result.getCode());
        assertEquals("permission denied", result.getMsg());
        verify(locationService, never()).deleteNodes(any());
    }
}

