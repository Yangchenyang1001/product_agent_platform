package com.ycy.core.task;

import com.ycy.core.service.KnowledgeImportTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KnowledgeImportPollingTask {
    @Autowired
    private KnowledgeImportTaskService knowledgeImportTaskService;


    @Scheduled(fixedDelayString = "#{@kmpConfig.knowledgeImportPollIntervalMs}") //可以写表达式
    //@Scheduled(fixedDelay=5000) //直接写数值
    public void pollAndCleanImportTasks() {
        log.info("开始执行知识导入后台轮询");
        knowledgeImportTaskService.pollProcessingTasks();
        log.info("完成执行知识导入后台轮询");
    }

}
