package com.ruslanlesko.pichub.core.controller;

import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

class PictureHandlerTest {
    private static long USER_ID = 42;
    private static long PIC_ID = 24;

    private PictureDao dao = mock(PictureDao.class);

    private PictureHandler subject;

    @BeforeEach
    void setUp() {
        when(dao.findIdsForUser(USER_ID)).thenReturn(List.of(PIC_ID));
    }

    @Test
    void testGetIdsForUserOK() {
        subject = new PictureHandler(dao);
        String response = subject.getIdsForUser(USER_ID);

        assertEquals("[" + PIC_ID + "]", response);
    }
}
