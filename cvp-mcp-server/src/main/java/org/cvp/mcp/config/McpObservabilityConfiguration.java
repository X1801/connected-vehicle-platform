package org.cvp.mcp.config;
import jakarta.servlet.Filter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class McpObservabilityConfiguration { @Bean Filter correlationMdcFilter(){return (request,response,chain)->{String correlation=request instanceof jakarta.servlet.http.HttpServletRequest h?h.getHeader("X-Correlation-Id"):null;if(correlation!=null&&!correlation.isBlank())MDC.put("correlationId",correlation);try{chain.doFilter(request,response);}finally{MDC.remove("correlationId");}};} }
