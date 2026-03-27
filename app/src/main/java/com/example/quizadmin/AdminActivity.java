package com.example.quizadmin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends AppCompatActivity {
    private EditText etServerIp, etServerPort, etAdminName;
    private Button btnConnect, btnStartQuiz, btnEndQuiz;
    private ListView lvPlayers;
    private TextView tvStatus, tvWinner;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;
    private PlayerListAdapter playerAdapter;
    private List<String> playerList = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private long start=0;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // 初始化视图
        etServerIp = findViewById(R.id.etServerIp);
        etServerIp.setText("192.168.10.17");
        etServerPort = findViewById(R.id.etServerPort);
        etAdminName = findViewById(R.id.etAdminName);
        etAdminName.setText("admin");
        btnConnect = findViewById(R.id.btnConnect);
        btnStartQuiz = findViewById(R.id.btnStartQuiz);
        btnEndQuiz = findViewById(R.id.btnEndQuiz);
        lvPlayers = findViewById(R.id.lvPlayers);
        tvStatus = findViewById(R.id.tvStatus);
        tvWinner = findViewById(R.id.tvWinner);

        // 初始化适配器
        playerAdapter = new PlayerListAdapter(this, playerList);
        lvPlayers.setAdapter(playerAdapter);

        // 初始状态设置
        btnStartQuiz.setEnabled(false);
        btnEndQuiz.setEnabled(false);

        // 连接按钮点击事件
        btnConnect.setOnClickListener(v -> {
            if (!isConnected) {
                connectToServer();
            } else {
                disconnectFromServer();
            }
        });

        // 开始抢答按钮点击事件
        btnStartQuiz.setOnClickListener(v -> {
            if (isConnected && out != null) {
                start=System.currentTimeMillis();
                sendMessage("start_quiz", "");
                tvWinner.setText("等待选手抢答...");
                btnStartQuiz.setEnabled(false);
                btnEndQuiz.setEnabled(true);
            }
        });

        // 结束抢答按钮点击事件
        btnEndQuiz.setOnClickListener(v -> {
            if (isConnected && out != null) {
                sendMessage("end_quiz", "");
                tvWinner.setText("结束抢答");
                btnEndQuiz.setEnabled(false);
                btnStartQuiz.setEnabled(true);
            }
        });
    }

    // 连接到服务器
    private void connectToServer() {
        String ip = etServerIp.getText().toString().trim();
        String portStr = etServerPort.getText().toString().trim();
        String adminName = etAdminName.getText().toString().trim();

        if (ip.isEmpty() || portStr.isEmpty() || adminName.isEmpty()) {
            Toast.makeText(this, "请填写所有信息", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (Throwable e) {
            Toast.makeText(this, "端口号无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 在新线程中进行网络连接
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 发送管理员身份信息
                out.println("admin:" + adminName + ":connect");

                isConnected = true;
                handler.post(() -> {
                    tvStatus.setText("已连接到服务器");
                    btnConnect.setText("断开连接");
                    btnStartQuiz.setEnabled(true);
                    etServerIp.setEnabled(false);
                    etServerPort.setEnabled(false);
                    etAdminName.setEnabled(false);
                    Toast.makeText(AdminActivity.this, "与服务器连接成功", Toast.LENGTH_SHORT).show();
                });

                // 开始监听服务器消息
                listenForMessages();

            } catch (Throwable e) {
                handler.post(() -> {
                    Toast.makeText(AdminActivity.this, "连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    tvStatus.setText("连接失败");
                });
            }
        }).start();
    }

    // 断开与服务器的连接
    private void disconnectFromServer() {
        new Thread(() -> {
            try {
                if (socket != null) {
                    socket.close();
                }
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }

                isConnected = false;
                handler.post(() -> {
                    tvStatus.setText("未连接");
                    btnConnect.setText("连接服务器");
                    btnStartQuiz.setEnabled(false);
                    btnEndQuiz.setEnabled(false);
                    etServerIp.setEnabled(true);
                    etServerPort.setEnabled(true);
                    etAdminName.setEnabled(true);
                    playerList.clear();
                    playerAdapter.notifyDataSetChanged();
                    tvWinner.setText("");
                    Toast.makeText(AdminActivity.this, "已断开连接", Toast.LENGTH_SHORT).show();
                });

            } catch (Throwable e) {
                handler.post(() -> {
                    Toast.makeText(AdminActivity.this, "断开连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // 监听服务器消息
    private void listenForMessages() {
        new Thread(() -> {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    handleServerMessage(message);
                }

                // 如果循环结束，说明连接已断开
                handler.post(() -> {
                    Toast.makeText(AdminActivity.this, "与服务器的连接已断开", Toast.LENGTH_SHORT).show();
                    disconnectFromServer();
                });

            } catch (Throwable e) {
                handler.post(() -> {
                    if (isConnected) {
                        Toast.makeText(AdminActivity.this, "接收消息错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        disconnectFromServer();
                    }
                });
            }
        }).start();
    }

    // 处理服务器消息
    private void handleServerMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "player_list":
                    JSONArray playersArray = json.getJSONArray("players");
                    playerList.clear();
                    for (int i = 0; i < playersArray.length(); i++) {
                        playerList.add(playersArray.getString(i));
                    }
                    handler.post(() -> playerAdapter.notifyDataSetChanged());
                    break;

                case "player_joined":
                    String joinedPlayer = json.getString("name");
                    String joinTime = json.getString("time");
                    handler.post(() -> {
                        if (!playerList.contains(joinedPlayer)) {
                            playerList.add(joinedPlayer);
                            playerAdapter.notifyDataSetChanged();
                            Toast.makeText(AdminActivity.this, joinedPlayer + " 已加入 (" + joinTime + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                    break;

                case "player_left":
                    String leftPlayer = json.getString("name");
                    String leftTime = json.getString("time");
                    handler.post(() -> {
                        playerList.remove(leftPlayer);
                        playerAdapter.notifyDataSetChanged();
                        Toast.makeText(AdminActivity.this, leftPlayer + " 已离开 (" + leftTime + ")", Toast.LENGTH_SHORT).show();
                    });
                    break;

                case "player_answered":
                    long costTime = System.currentTimeMillis()-start;
                    String answerPlayer = json.getString("name");
                    String answerTime = json.getString("time");
                    handler.post(() -> {
                        tvWinner.setText("抢答成功者: " + answerPlayer +"，用时:"+costTime+"ms");
                        btnEndQuiz.setEnabled(true);
                    });
                    break;
                case "error":
                    String message1 = json.getString("message");
                    handler.post(() -> {
                        Toast.makeText(AdminActivity.this, message1, Toast.LENGTH_SHORT).show();
                    });
                    break;
            }

        } catch (Throwable e) {
            handler.post(() -> {
                Toast.makeText(AdminActivity.this, "解析消息错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    // 发送消息到服务器
    private void sendMessage(String type, String data) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("data", data);
                out.println(json);
            } catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(AdminActivity.this, "创建消息错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isConnected) {
            disconnectFromServer();
        }
    }
}
