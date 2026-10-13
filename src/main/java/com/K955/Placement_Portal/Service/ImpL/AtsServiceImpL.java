package com.K955.Placement_Portal.Service.ImpL;

import com.K955.Placement_Portal.DTOs.Resume.AtsReportResponse;
import com.K955.Placement_Portal.Entity.AtsReport;
import com.K955.Placement_Portal.Entity.Resume;
import com.K955.Placement_Portal.Entity.Student;
import com.K955.Placement_Portal.Exceptions.ResourceNotFoundException;
import com.K955.Placement_Portal.Mapper.AtsReportMapper;
import com.K955.Placement_Portal.Repository.AtsReportRepository;
import com.K955.Placement_Portal.Repository.AtsReportSpecification;
import com.K955.Placement_Portal.Repository.ResumeRepository;
import com.K955.Placement_Portal.Repository.StudentRepository;
import com.K955.Placement_Portal.Service.AtsService;
import com.K955.Placement_Portal.Util.S3FileStorageUtil;
import com.K955.Placement_Portal.Util.PdfTextExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtsServiceImpL implements AtsService {

    private final ResumeRepository resumeRepository;
    private final AtsReportRepository atsReportRepository;
    private final StudentRepository studentRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AtsReportMapper atsReportMapper;
    private final S3FileStorageUtil s3FileStorageUtil;

    @Override
    public AtsReportResponse analyzeResume(Long userId) throws IOException {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", userId.toString()));

        Resume resume = resumeRepository.findByStudentUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", student.getId().toString()));

        String resumeText;
        try (InputStream resumeStream = s3FileStorageUtil.getFileStream(resume.getFilePath())) {
            resumeText = pdfTextExtractor.extractText(resumeStream);
        } catch (Exception e) {
            throw new IOException("Failed to read resume from storage: " + e.getMessage(), e);
        }

        String response = chatClient.prompt()
                .user(resumeText)
                .call()
                .content();

        AtsReportResponse parsed = objectMapper.readValue(response, AtsReportResponse.class);

        AtsReport atsReport = AtsReport.builder()
                .resume(resume)
                .overallScore(parsed.overallScore())
                .skillScore(parsed.skillScore())
                .experienceScore(parsed.experienceScore())
                .educationScore(parsed.educationScore())
                .formattingScore(parsed.formattingScore())
                .missingKeywords(parsed.missingKeywords())
                .suggestions(parsed.suggestions())
                .strengths(parsed.strengths())
                .build();

        AtsReport saved = atsReportRepository.save(atsReport);

        return atsReportMapper.toAtsReportResponse(saved);
    }

    @Override
    public Page<AtsReportResponse> getAtsHistory(Long userId, String search, Pageable pageable) {
        var spec = AtsReportSpecification.searchReport(search, userId);
        return atsReportRepository.findAll(spec, pageable).map(atsReportMapper::toAtsReportResponse);
    }

    @Override
    public AtsReportResponse getAtsReport(Long reportId, Long userId) {
        AtsReport atsReport = atsReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("ATS Report", reportId.toString()));

        if (!atsReport.getResume().getStudent().getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to view this report");
        }

        return atsReportMapper.toAtsReportResponse(atsReport);
    }
}
