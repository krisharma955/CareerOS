package com.K955.Placement_Portal.Service.ImpL;

import com.K955.Placement_Portal.DTOs.Resume.ResumeResponse;
import com.K955.Placement_Portal.Entity.Resume;
import com.K955.Placement_Portal.Entity.Student;
import com.K955.Placement_Portal.Exceptions.BadRequestException;
import com.K955.Placement_Portal.Exceptions.ResourceNotFoundException;
import com.K955.Placement_Portal.Mapper.ResumeMapper;
import com.K955.Placement_Portal.Repository.ResumeRepository;
import com.K955.Placement_Portal.Repository.StudentRepository;
import com.K955.Placement_Portal.Service.ResumeService;
import com.K955.Placement_Portal.Util.S3FileStorageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpL implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final StudentRepository studentRepository;
    private final S3FileStorageUtil s3FileStorageUtil;
    private final ResumeMapper resumeMapper;

    @Override
    public ResumeResponse uploadResume(Long userId, MultipartFile file) throws IOException {
        if(file.isEmpty()) {
            throw new BadRequestException("File is Empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf"))) {
            throw new BadRequestException("Invalid file type, only PDF allowed");
        }

        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", userId.toString()));

        Optional<Resume> existingResume = resumeRepository.findByStudentUserId(userId);

        if(existingResume.isPresent()) {
            s3FileStorageUtil.deleteFile(existingResume.get().getFilePath());

            Resume resume = existingResume.get();
            String filePath = s3FileStorageUtil.saveFile(file, userId);
            resume.setFileName(file.getOriginalFilename());
            resume.setFilePath(filePath);
            resume.setFileType(contentType);
            resume.setFileSize(file.getSize());

            Resume saved = resumeRepository.save(resume);
            return resumeMapper.toResumeResponse(saved);
        }
        else {
            String filePath = s3FileStorageUtil.saveFile(file, userId);

            Resume resume = Resume.builder()
                    .student(student)
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .fileType(contentType)
                    .fileSize(file.getSize())
                    .build();

            Resume saved = resumeRepository.save(resume);
            return resumeMapper.toResumeResponse(saved);
        }

    }

    @Override
    public ResumeResponse getResumeMetaData(Long userId) {
        Resume resume = resumeRepository.findByStudentUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", userId.toString()));

        return resumeMapper.toResumeResponse(resume);
    }

    @Override
    public String downloadResume(Long userId) throws MalformedURLException {
        Resume resume = resumeRepository.findByStudentUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", userId.toString()));

        return s3FileStorageUtil.getPresignedDownloadUrl(resume.getFilePath());
    }
}
