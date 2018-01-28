/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.controllers.bottomsheet;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;

import com.bluelinelabs.conductor.RouterTransaction;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.ApplicationWideMessageHolder;
import com.nextcloud.talk.utils.ShareUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import studio.carbonylgroup.textfieldboxes.ExtendedEditText;
import studio.carbonylgroup.textfieldboxes.TextFieldBoxes;

@AutoInjector(NextcloudTalkApplication.class)
public class EntryMenuController extends BaseController {
    private static final String TAG = "EntryMenuController";

    @BindView(R.id.ok_button)
    Button proceedButton;

    @BindView(R.id.extended_edit_text)
    ExtendedEditText editText;

    @BindView(R.id.text_field_boxes)
    TextFieldBoxes textFieldBoxes;

    @Inject
    EventBus eventBus;

    @Inject
    UserUtils userUtils;

    private int operationCode;
    private Room room;
    private Intent shareIntent;
    private String packageName;
    private String name;

    public EntryMenuController(Bundle args) {
        super(args);
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE);
        this.room = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));

        if (args.containsKey(BundleKeys.KEY_SHARE_INTENT)) {
            this.shareIntent = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_SHARE_INTENT));
        }

        this.name = args.getString(BundleKeys.KEY_APP_ITEM_NAME, "");
        this.packageName = args.getString(BundleKeys.KEY_APP_ITEM_PACKAGE_NAME, "");
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_entry_menu, container, false);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (ApplicationWideMessageHolder.getInstance().getMessageType() != null &&
                ApplicationWideMessageHolder.getInstance().getMessageType().equals(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG)) {
            textFieldBoxes.setError(getResources().getString(R.string.nc_wrong_password), true);
            ApplicationWideMessageHolder.getInstance().setMessageType(null);
            if (proceedButton.isEnabled()) {
                proceedButton.setEnabled(false);
                proceedButton.setAlpha(0.7f);
            }
        }
    }

    @OnClick(R.id.ok_button)
    public void onProceedButtonClick() {
        Bundle bundle;
        if (operationCode == 99) {
            UserEntity userEntity = userUtils.getCurrentUser();

            if (userEntity != null) {
                eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
                bundle = new Bundle();
                bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
                bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(userEntity));
                bundle.putString(BundleKeys.KEY_CALL_PASSWORD, editText.getText().toString());
                getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle)));
            }

        } else if (operationCode != 7) {
            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
            bundle = new Bundle();
            if (operationCode == 4 || operationCode == 6) {
                room.setPassword(editText.getText().toString());
            } else {
                room.setName(editText.getText().toString());
            }
            bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, operationCode);
            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle)));
        } else {
            if (getActivity() != null) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(getActivity(),
                        editText.getText().toString(), userUtils, room));
                Intent intent = new Intent(shareIntent);
                intent.setComponent(new ComponentName(packageName, name));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(intent);
                eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
            }
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && proceedButton.isEnabled()) {
                proceedButton.callOnClick();
                return true;
            }
            return false;
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    if (operationCode == 2) {
                        if (room.getName() == null || !room.getName().equals(s.toString())) {
                            if (!proceedButton.isEnabled()) {
                                proceedButton.setEnabled(true);
                                proceedButton.setAlpha(1.0f);
                            }
                        } else {
                            if (proceedButton.isEnabled()) {
                                proceedButton.setEnabled(false);
                                proceedButton.setAlpha(0.7f);
                            }
                            textFieldBoxes.setError(getResources().getString(R.string.nc_call_name_is_same),
                                    true);
                        }
                    } else {
                        if (!proceedButton.isEnabled()) {
                            proceedButton.setEnabled(true);
                            proceedButton.setAlpha(1.0f);
                        }
                    }
                } else {
                    if (proceedButton.isEnabled()) {
                        proceedButton.setEnabled(false);
                        proceedButton.setAlpha(0.7f);
                    }
                }
            }
        });

        String labelText = "";
        switch (operationCode) {
            case 2:
                labelText = getResources().getString(R.string.nc_call_name);
                break;
            case 4:
                labelText = getResources().getString(R.string.nc_new_password);
                break;
            case 6:
            case 7:
            case 99:
                labelText = getResources().getString(R.string.nc_password);
                break;
            default:
                break;
        }

        textFieldBoxes.setLabelText(labelText);
        editText.requestFocus();
    }
}
