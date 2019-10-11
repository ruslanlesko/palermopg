package com.ruslanlesko.pichub.core.controller;

import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

class PictureHandlerTest {
    private static long USER_ID = 42;
    private static long PIC_ID = 24;
    private static String TOKEN = "token";

    private PictureDao dao = mock(PictureDao.class);

    private PictureHandler subject;

    @BeforeEach
    void setUp() {
        when(dao.findIdsForUser(USER_ID)).thenReturn(List.of(PIC_ID));
    }

    @Test
    void testGetIdsForUserOK() {
        subject = new PictureHandler(dao);
        String response = subject.getIdsForUser(USER_ID, TOKEN);

        assertEquals("[" + PIC_ID + "]", response);
    }

    @Test
    void testGetIdsForUserNoToken() {
        assertThrows(AuthorizationException.class, () -> {
            subject = new PictureHandler(dao);
            subject.getIdsForUser(USER_ID, null);
        });
    }
}
