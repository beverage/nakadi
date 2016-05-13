package de.zalando.aruha.nakadi.domain;

import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class EventTypeStatistics {
    @NotNull
    @Min(value = 0, message = "can't be less then zero")
    private Integer messagesPerMinute;
    @NotNull
    @Min(value = 1, message = "can't be less then 1")
    private Integer messageSize;
    @NotNull
    @Min(value = 1, message = "at least one reader expected")
    private Integer readParallelism;
    @NotNull
    @Min(value = 1, message = "at least one writer expected")
    private Integer writeParallelism;

    public Integer getMessagesPerMinute() {
        return messagesPerMinute;
    }

    public void setMessagesPerMinute(final Integer messagesPerMinute) {
        this.messagesPerMinute = messagesPerMinute;
    }

    public Integer getMessageSize() {
        return messageSize;
    }

    public void setMessageSize(final Integer messageSize) {
        this.messageSize = messageSize;
    }

    public Integer getReadParallelism() {
        return readParallelism;
    }

    public void setReadParallelism(final Integer readParallelism) {
        this.readParallelism = readParallelism;
    }

    public Integer getWriteParallelism() {
        return writeParallelism;
    }

    public void setWriteParallelism(final Integer writeParallelism) {
        this.writeParallelism = writeParallelism;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final EventTypeStatistics that = (EventTypeStatistics) o;
        return Objects.equals(messagesPerMinute, that.messagesPerMinute)
                && Objects.equals(messageSize, that.messageSize)
                && Objects.equals(readParallelism, that.readParallelism)
                && Objects.equals(writeParallelism, that.writeParallelism);
    }

    @Override
    public int hashCode() {
        int result = messagesPerMinute != null ? messagesPerMinute.hashCode() : 0;
        result = 31 * result + (messageSize != null ? messageSize.hashCode() : 0);
        result = 31 * result + (readParallelism != null ? readParallelism.hashCode() : 0);
        result = 31 * result + (writeParallelism != null ? writeParallelism.hashCode() : 0);
        return result;
    }
}