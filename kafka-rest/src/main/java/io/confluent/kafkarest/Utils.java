/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.kafkarest;

import io.confluent.rest.exceptions.RestException;
import io.confluent.rest.exceptions.RestServerErrorException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.RetriableException;
import org.eclipse.jetty.util.StringUtil;

import javax.ws.rs.core.Response;
import java.util.concurrent.ExecutionException;

import static io.confluent.kafkarest.Errors.KAFKA_ERROR_ERROR_CODE;

public class Utils {

  public static final String UNEXPECTED_PRODUCER_EXCEPTION
      = "Unexpected non-Kafka exception returned by Kafka";

  public static int errorCodeFromProducerException(Throwable e) {
    if (e instanceof  AuthenticationException || e instanceof AuthorizationException) {
      return  Response.Status.FORBIDDEN.getStatusCode();
    } else if (e instanceof RetriableException) {
      return Errors.KAFKA_RETRIABLE_ERROR_ERROR_CODE;
    } else if (e instanceof KafkaException) {
      return KAFKA_ERROR_ERROR_CODE;
    } else {
      // We shouldn't see any non-Kafka exceptions, but this covers us in case we do see an
      // unexpected error. In that case we fail the entire request -- this loses information
      // since some messages may have been produced correctly, but is the right thing to do from
      // a REST perspective since there was an internal error with the service while processing
      // the request.
      throw new RestServerErrorException(UNEXPECTED_PRODUCER_EXCEPTION,
                                         RestServerErrorException.DEFAULT_ERROR_CODE, e
      );
    }
  }

  public static RestServerErrorException convertProducerException(KafkaException exception) {
    if (StringUtil.startsWithIgnoreCase(exception.getMessage(), "Invalid partition")) {
      throw Errors.partitionNotFoundException();
    } else if (exception instanceof AuthenticationException) {
      return Errors.authenticationException(exception.getMessage());
    } else if (exception instanceof AuthorizationException) {
      return Errors.authorizationException(exception.getMessage());
    } else {
      throw Errors.kafkaErrorException(exception);
    }
  }

  public static RestException convertConsumerException(Exception exception) {
    if (exception != null && !(exception instanceof RestException)) {
      if (exception instanceof AuthenticationException) {
        return Errors.authenticationException(exception.getMessage());
      } else if (exception instanceof AuthorizationException) {
        return Errors.authorizationException(exception.getMessage());
      } else {
        return Errors.kafkaErrorException(exception);
      }
    }

    return (RestException) exception;
  }

  public static RestServerErrorException convertAdminException(Exception exception) {
    if (exception instanceof ExecutionException) {
      final Throwable cause = exception.getCause();
      if (cause != null) {
        if (cause instanceof AuthenticationException) {
          return Errors.authenticationException(cause.getMessage());
        } else if (cause instanceof AuthorizationException) {
          return Errors.authorizationException(cause.getMessage());
        }
      }
    }

    return Errors.kafkaErrorException(exception);
  }
}
