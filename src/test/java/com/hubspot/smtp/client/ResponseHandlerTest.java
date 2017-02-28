package com.hubspot.smtp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.smtp.DefaultSmtpResponse;
import io.netty.handler.codec.smtp.SmtpResponse;

public class ResponseHandlerTest {
  private ResponseHandler responseHandler;
  private ChannelHandlerContext context;
  private static final DefaultSmtpResponse SMTP_RESPONSE = new DefaultSmtpResponse(250);

  @Before
  public void setup() {
    responseHandler = new ResponseHandler();
    context = mock(ChannelHandlerContext.class);
  }

  @Test
  public void itCompletesExceptionallyIfAnExceptionIsCaught() throws Exception {
    CompletableFuture<SmtpResponse[]> f = responseHandler.createResponseFuture(1);
    Exception testException = new Exception("test");

    responseHandler.exceptionCaught(context, testException);

    assertThat(f.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(f::get).isInstanceOf(ExecutionException.class).hasCause(testException);
  }

  @Test
  public void itCompletesWithAResponseWhenHandled() throws Exception {
    CompletableFuture<SmtpResponse[]> f = responseHandler.createResponseFuture(1);

    responseHandler.channelRead(context, SMTP_RESPONSE);

    assertThat(f.isCompletedExceptionally()).isFalse();
    assertThat(f.get()).isEqualTo(new SmtpResponse[] { SMTP_RESPONSE });
  }

  @Test
  public void itDoesNotCompleteWhenSomeOtherObjectIsRead() throws Exception {
    CompletableFuture<SmtpResponse[]> f = responseHandler.createResponseFuture(1);

    responseHandler.channelRead(context, "unexpected");

    assertThat(f.isDone()).isFalse();
  }

  @Test
  public void itOnlyCreatesOneResponseFutureAtATime() {
    assertThat(responseHandler.createResponseFuture(1)).isNotNull();

    assertThatThrownBy(() -> responseHandler.createResponseFuture(1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot wait for a response while one is already pending");
  }

  @Test
  public void itOnlyCreatesOneResponseFutureAtATimeForMultipleResponses() {
    assertThat(responseHandler.createResponseFuture(2)).isNotNull();

    assertThatThrownBy(() -> responseHandler.createResponseFuture(1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot wait for a response while one is already pending");
  }

  @Test
  public void itCanCreateAFutureThatWaitsForMultipleReponses() throws Exception {
    CompletableFuture<SmtpResponse[]> f = responseHandler.createResponseFuture(3);
    SmtpResponse response1 = new DefaultSmtpResponse(250, "1");
    SmtpResponse response2 = new DefaultSmtpResponse(250, "2");
    SmtpResponse response3 = new DefaultSmtpResponse(250, "3");

    responseHandler.channelRead(context, response1);

    assertThat(f.isDone()).isFalse();

    responseHandler.channelRead(context, response2);
    responseHandler.channelRead(context, response3);

    assertThat(f.isDone()).isTrue();

    assertThat(f.isCompletedExceptionally()).isFalse();
    assertThat(f.get()[0]).isEqualTo(response1);
    assertThat(f.get()[1]).isEqualTo(response2);
    assertThat(f.get()[2]).isEqualTo(response3);
  }

  @Test
  public void itCanCreateAFutureInTheCallbackForAPreviousFuture() throws Exception {
    CompletableFuture<SmtpResponse[]> future = responseHandler.createResponseFuture(1);

    CompletableFuture<Void> assertion = future.thenRun(() -> assertThat(responseHandler.createResponseFuture(1)).isNotNull());

    responseHandler.channelRead(context, SMTP_RESPONSE);

    assertion.get();
  }

  @Test
  public void itCanFailMultipleResponseFuturesAtAnyTime() throws Exception {
    CompletableFuture<SmtpResponse[]> f = responseHandler.createResponseFuture(3);
    Exception testException = new Exception("test");

    responseHandler.exceptionCaught(context, testException);

    assertThat(f.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(f::get).isInstanceOf(ExecutionException.class).hasCause(testException);
  }

  @Test
  public void itCanCreateNewFuturesOnceAResponseHasArrived() throws Exception {
    responseHandler.createResponseFuture(1);
    responseHandler.channelRead(context, SMTP_RESPONSE);

    responseHandler.createResponseFuture(1);
  }

  @Test
  public void itCanCreateNewFuturesOnceATheExpectedResponsesHaveArrived() throws Exception {
    responseHandler.createResponseFuture(2);
    responseHandler.channelRead(context, SMTP_RESPONSE);
    responseHandler.channelRead(context, SMTP_RESPONSE);

    responseHandler.createResponseFuture(1);
  }

  @Test
  public void itCanCreateNewFuturesOnceAnExceptionIsHandled() throws Exception {
    responseHandler.createResponseFuture(1);
    responseHandler.exceptionCaught(context, new Exception("test"));

    responseHandler.createResponseFuture(1);
  }
}
