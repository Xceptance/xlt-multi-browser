package xltutil.proxy;

import java.net.URL;

import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.http.HttpClient.Builder;
import org.openqa.selenium.remote.internal.OkHttpClient;

public class ProxyHttpClient implements org.openqa.selenium.remote.http.HttpClient.Factory
{
    private final OkHttpClient okHttpClient;

    public ProxyHttpClient(OkHttpClient okHttpClient)
    {
        this.okHttpClient = okHttpClient;
    }

    @Override
    public org.openqa.selenium.remote.http.HttpClient createClient(URL url)
    {
        return (HttpClient) okHttpClient;
    }

    @Override
    public Builder builder()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cleanupIdleClients()
    {

    }
}
