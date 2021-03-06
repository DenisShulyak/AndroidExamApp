package com.example.examapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.getstream.sdk.chat.view.ChannelHeaderView;
import com.getstream.sdk.chat.view.MessageListView;
import com.getstream.sdk.chat.view.messageinput.MessageInputView;
import com.getstream.sdk.chat.viewmodel.ChannelHeaderViewModel;
import com.getstream.sdk.chat.viewmodel.ChannelHeaderViewModelBinding;
import com.getstream.sdk.chat.viewmodel.MessageInputViewModel;
import com.getstream.sdk.chat.viewmodel.MessageInputViewModelBinding;
import com.getstream.sdk.chat.viewmodel.factory.ChannelViewModelFactory;
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModel;
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModel.Mode.Normal;
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModel.Mode.Thread;
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModel.State.NavigateUp;
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModelBinding;

import java.util.LinkedList;
import java.util.List;

import io.getstream.chat.android.client.models.Channel;
import io.getstream.chat.android.client.models.User;
import io.getstream.chat.android.livedata.ChatDomain;
import io.getstream.chat.android.livedata.controller.ChannelController;
import kotlin.Unit;

public class ChannelActivity extends AppCompatActivity {

    private final static String CID_KEY = "key:cid";

    public static Intent newIntent(Context context, Channel channel) {
        final Intent intent = new Intent(context, ChannelActivity.class);
        intent.putExtra(CID_KEY, channel.getCid());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        String cid = getIntent().getStringExtra(CID_KEY);
        if (cid == null) {
            throw new IllegalStateException("Specifying a channel id is required when starting ChannelActivity");
        }

        // Step 0 - Get View references
        MessageListView messageListView = findViewById(R.id.messageListView);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        ChannelHeaderView channelHeaderView = findViewById(R.id.channelHeaderView);
        MessageInputView messageInputView = findViewById(R.id.messageInputView);

        // Step 1 - Create 3 separate ViewModels for the views so it's easy to customize one of the components
        ChannelViewModelFactory factory = new ChannelViewModelFactory(cid);
        ViewModelProvider provider = new ViewModelProvider(this, factory);
        ChannelHeaderViewModel channelHeaderViewModel = provider.get(ChannelHeaderViewModel.class);
        MessageListViewModel messageListViewModel = provider.get(MessageListViewModel.class);
        MessageInputViewModel messageInputViewModel = provider.get(MessageInputViewModel.class);

        // Set custom AttachmentViewHolderFactory
        messageListView.setAttachmentViewHolderFactory(new MyAttachmentViewHolderFactory());

        // Step 2 - Bind the view and ViewModels, they are loosely coupled so it's easy to customize
        ChannelHeaderViewModelBinding.bind(channelHeaderViewModel, channelHeaderView, this);
        MessageListViewModelBinding.bind(messageListViewModel, messageListView, this);
        MessageInputViewModelBinding.bind(messageInputViewModel, messageInputView, this);

        // Step 3 - Let the message input know when we open a thread
        messageListViewModel.getMode().observe(this, mode -> {
            if (mode instanceof Thread) {
                messageInputViewModel.setActiveThread(((Thread) mode).getParentMessage());
            } else if (mode instanceof Normal) {
                messageInputViewModel.resetThread();
            }
        });
        // Step 4 - Let the message input know when we are editing a message
        messageListView.setOnMessageEditHandler(message -> {
            messageInputViewModel.getEditMessage().postValue(message);
            return Unit.INSTANCE;
        });
        // Custom typing info header bar
        TextView typingHeaderView = findViewById(R.id.typingHeaderView);
        String nobodyTyping = "nobody is typing";
        typingHeaderView.setText(nobodyTyping);

// Obtain a ChannelController
        ChatDomain.instance().getUseCases().getGetChannelController().invoke(cid).enqueue((result) -> {
            ChannelController channelController = result.data();

            // Observe typing users
            channelController.getTyping().observe(this, typingState -> {
                if (typingState.getUsers().isEmpty()) {
                    typingHeaderView.setText(nobodyTyping);
                } else {
                    List<String> userNames = new LinkedList<>();
                    for (User user : typingState.getUsers()) {
                        userNames.add((String) user.getExtraData().get("name"));
                    }
                    String typing = "typing: " + TextUtils.join(", ", userNames);
                    typingHeaderView.setText(typing);
                }
            });
            return Unit.INSTANCE;
        });

        // Step 5 - Handle navigate up state
        messageListViewModel.getState().observe(this, state -> {
            if (state instanceof NavigateUp) {
                finish();
            }
        });

        // Step 6 - Handle back button behaviour correctly when you're in a thread
        channelHeaderView.setOnBackClick(() -> {
            messageListViewModel.onEvent(MessageListViewModel.Event.BackButtonPressed.INSTANCE);
            return Unit.INSTANCE;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                channelHeaderView.getOnBackClick().invoke();
            }
        });
    }
}