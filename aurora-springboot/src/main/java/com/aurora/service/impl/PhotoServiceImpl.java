package com.aurora.service.impl;


import com.aurora.model.dto.PhotoAdminDTO;
import com.aurora.model.dto.PhotoAlbumAdminDTO;
import com.aurora.model.dto.PhotoDTO;
import com.aurora.entity.Photo;
import com.aurora.entity.PhotoAlbum;
import com.aurora.exception.BizException;
import com.aurora.mapper.PhotoAlbumMapper;
import com.aurora.mapper.PhotoMapper;
import com.aurora.service.PhotoAlbumService;
import com.aurora.service.PhotoService;
import com.aurora.utils.BeanCopyUtils;
import com.aurora.utils.PageUtils;
import com.aurora.model.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.aurora.constant.CommonConst.FALSE;
import static com.aurora.enums.PhotoAlbumStatusEnum.PUBLIC;

@Service
public class PhotoServiceImpl extends ServiceImpl<PhotoMapper, Photo> implements PhotoService {

    @Autowired
    private PhotoMapper photoMapper;

    @Autowired
    private PhotoAlbumMapper photoAlbumMapper;

    @Autowired
    private PhotoAlbumService photoAlbumService;


    @Override
    public PageResult<PhotoAdminDTO> listPhotos(ConditionVO conditionVO) {
        // 查询照片列表
        Page<Photo> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        Page<Photo> photoPage = photoMapper.selectPage(page, new LambdaQueryWrapper<Photo>()
                .eq(Objects.nonNull(conditionVO.getAlbumId()), Photo::getAlbumId, conditionVO.getAlbumId())
                .eq(Photo::getIsDelete, conditionVO.getIsDelete())
                .orderByDesc(Photo::getId)
                .orderByDesc(Photo::getUpdateTime));
        List<PhotoAdminDTO> photos = BeanCopyUtils.copyList(photoPage.getRecords(), PhotoAdminDTO.class);
        return new PageResult<>(photos, (int) photoPage.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePhoto(PhotoInfoVO photoInfoVO) {
        Photo photo = BeanCopyUtils.copyObject(photoInfoVO, Photo.class);
        photoMapper.updateById(photo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void savePhotos(PhotoVO photoVO) {
        List<Photo> photoList = photoVO.getPhotoUrls().stream().map(item -> Photo.builder()
                        .albumId(photoVO.getAlbumId())
                        .photoName(IdWorker.getIdStr())
                        .photoSrc(item)
                        .build())
                .collect(Collectors.toList());
        this.saveBatch(photoList);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePhotosAlbum(PhotoVO photoVO) {
        List<Photo> photoList = photoVO.getPhotoIds().stream().map(item -> Photo.builder()
                        .id(item)
                        .albumId(photoVO.getAlbumId())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(photoList);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePhotoDelete(DeleteVO deleteVO) {
        // 更新照片状态
        List<Photo> photoList = deleteVO.getIds().stream().map(item -> Photo.builder()
                        .id(item)
                        .isDelete(deleteVO.getIsDelete())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(photoList);
        // 若恢复照片所在的相册已删除，恢复相册
        if (deleteVO.getIsDelete().equals(FALSE)) {
            List<PhotoAlbum> photoAlbumList = photoMapper.selectList(new LambdaQueryWrapper<Photo>()
                            .select(Photo::getAlbumId)
                            .in(Photo::getId, deleteVO.getIds())
                            .groupBy(Photo::getAlbumId))
                    .stream()
                    .map(item -> PhotoAlbum.builder()
                            .id(item.getAlbumId())
                            .isDelete(FALSE)
                            .build())
                    .collect(Collectors.toList());
            photoAlbumService.updateBatchById(photoAlbumList);
        }
    }

    @Override
    public PageResult<PhotoAlbumAdminDTO> listPhotoAlbumBacks(ConditionVO conditionVO) {
        // 查询相册数量
        Integer count = photoAlbumMapper.selectCount(new LambdaQueryWrapper<PhotoAlbum>()
                .like(StringUtils.isNotBlank(conditionVO.getKeywords()), PhotoAlbum::getAlbumName, conditionVO.getKeywords())
                .eq(PhotoAlbum::getIsDelete, FALSE));
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询相册信息
        List<PhotoAlbumAdminDTO> PhotoAlbumAdminDTOs = photoAlbumMapper.listPhotoAlbumsAdmin(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO);
        return new PageResult<>(PhotoAlbumAdminDTOs, count);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletePhotos(List<Integer> photoIdList) {
        photoMapper.deleteBatchIds(photoIdList);
    }

    @Override
    public PhotoDTO listPhotosByAlbumId(Integer albumId) {
        // 查询相册信息
        PhotoAlbum photoAlbum = photoAlbumService.getOne(new LambdaQueryWrapper<PhotoAlbum>()
                .eq(PhotoAlbum::getId, albumId)
                .eq(PhotoAlbum::getIsDelete, FALSE)
                .eq(PhotoAlbum::getStatus, PUBLIC.getStatus()));
        if (Objects.isNull(photoAlbum)) {
            throw new BizException("相册不存在");
        }
        // 查询照片列表
        Page<Photo> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
        List<String> photos = photoMapper.selectPage(page, new LambdaQueryWrapper<Photo>()
                        .select(Photo::getPhotoSrc)
                        .eq(Photo::getAlbumId, albumId)
                        .eq(Photo::getIsDelete, FALSE)
                        .orderByDesc(Photo::getId))
                .getRecords()
                .stream()
                .map(Photo::getPhotoSrc)
                .collect(Collectors.toList());
        return PhotoDTO.builder()
                .photoAlbumCover(photoAlbum.getAlbumCover())
                .photoAlbumName(photoAlbum.getAlbumName())
                .photos(photos)
                .build();
    }


}
