package xltutil.proxy;

import java.net.URL;

import org.apache.http.client.HttpClient;
import org.openqa.selenium.remote.http.HttpClient.Builder;
import org.openqa.selenium.remote.internal.OkHttpClient;

public class ProxyHttpClient implements org.openqa.selenium.remote.http.HttpClient.Factory
{
    private final HttpClient httpClient;

    public ProxyHttpClient(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    @Override
    public org.openqa.selenium.remote.http.HttpClient createClient(URL url)
    {
        return new OkHttpClient((okhttp3.OkHttpClient) httpClient, url);
    }

    @Override
    public Builder builder()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void cleanupIdleClients()
    {
        // TODO Auto-generated method stub

    }
}
