package com.tencent.devops.process.yaml.pojo

import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation

class PipelineYamlViewLock(
    redisOperation: RedisOperation,
    projectId: String,
    repoHashId: String,
    expiredTimeInSeconds: Long = 60
) :
    RedisLock(
        redisOperation = redisOperation,
        lockKey = "pipeline:yaml:view:$projectId:$repoHashId",
        expiredTimeInSeconds = expiredTimeInSeconds
    )
