package com.ruslanlesko.pichub.core.dao.impl;

import com.ruslanlesko.pichub.core.dao.PictureDao;
import com.ruslanlesko.pichub.core.entity.Picture;
import org.junit.jupiter.api.Test;

public class FilePictureDaoTest {

    @Test
    public void testAddNewFile() {
        PictureDao pictureDao = new FilePictureDao();
        pictureDao.save(2, new Picture(1, new byte[3]));
    }
}
