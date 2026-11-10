package org.cvp.mcp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cvp.mcp.client.DownstreamClient;
import org.cvp.mcp.tool.McpToolResponse;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
class VehicleToolsTests { @Test void rejectsBlankVehicleIdWithoutCallingDownstream(){DownstreamClient c=new DownstreamClient("http://127.0.0.1:1","http://127.0.0.1:1","http://127.0.0.1:1",Duration.ofMillis(50),new SimpleMeterRegistry());VehicleTools tools=new VehicleTools(c,c,c);McpToolResponse result=tools.getVehicleCapability(" ");assertThat(result.status()).isEqualTo("ERROR");assertThat(result.error().code()).isEqualTo("INVALID_ARGUMENT");} }
