package com.example.keepitall;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Activity used for displaying the user's items (HomePage)
 * This page has multiple functionalities:
 *     1) Add an item
 *     2) Delete an item
 *     3) View an item's properties
 *     4) Sort items (soon)
 *     5) Filter items (soon)
 *     6) Search items (soon)
 *     7) Logout
 * Whenever an item is added or deleted, the total value of all items is updated
 * Whenever an item is added or deleted, the adapter is notified and the gridView is refreshed
 * Whenever an item is added or deleted, the itemManager is updated and synced with the database
 */
public class HomePageActivity extends AppCompatActivity implements SortOptions.SortOptionsListener {

    private GridView gridView;
    private boolean deleteMode = false;
    private ItemManager userItemManager;
    private ArrayList<Item> itemsToRemove = new ArrayList<>();
    private HomePageAdapter homePageAdapter;
    private TextView totalValueView;
    private Button deleteButton;
    private User user;
    private KeepItAll keepItAll = KeepItAll.getInstance();
    private Button logoutButton;
    private Button pictureButton;
    static final int REQUEST_IMAGE_CAPTURE = 2; // For taking pictures
    private ImageView TempImageView;
    private TextView usernameView;
    private String userName;
    private Button filterDateButton;
    private Button sortButton;
    private SearchView searchText;
    private ItemManager currentItemManager;
    private Date startDate;
    private Date endDate;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);
        totalValueView = findViewById(R.id.totalValueText);
        pictureButton = findViewById(R.id.take_picture_button);

        // Gets username
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            userName = extras.getString("username");

            // Ensure that the username is not null or empty
            if (userName != null && !userName.isEmpty()) {
                // get User's itemManager
                user = keepItAll.getUserByName(userName);
                if (user != null) {
                    userItemManager = user.getItemManager();

                    // sets username
                    usernameView = findViewById(R.id.nameText);
                    usernameView.setText(userName);

                    // set the Adapter for gridView
                    gridView = findViewById(R.id.gridView);
                    homePageAdapter = new HomePageAdapter(this, userItemManager);
                    gridView.setAdapter(homePageAdapter);

                    updateTotalValue(); // Gets the total Value

                    // gridView onClickListener for deletion or view item properties
                    gridView.setOnItemClickListener((parent, view, position, id) -> {
                        gridViewClickEvent(view, position);
                    });

                    // Add item button
                    AppCompatButton addButton = findViewById(R.id.addButton);
                    addButton.setOnClickListener(v -> {
                        Intent intent = new Intent(HomePageActivity.this, AddItemActivity.class);
                        startActivityForResult(intent, 1);
                    });

                    // Take picture button
                    pictureButton.setOnClickListener(v -> takePictureClickEvent());

                    // Go back to login screen if back button is pressed
                    logoutButton = findViewById(R.id.logoutButton);
                    logoutButton.setOnClickListener(v -> finish());

                    // Delete an item
                    deleteButton = findViewById(R.id.deleteButton);
                    deleteButton.setOnClickListener(v -> deleteButtonClickEvent());

                    // Filter the items
                    filterDateButton = findViewById(R.id.filterButton);
                    filterDateButton.setOnClickListener(v -> filterClickEvent(true));

                    // Sort the items
                    sortButton = findViewById(R.id.sortButton);
                    sortButton.setOnClickListener(v -> sortClickEvent());

                    // Search the items
                    searchText = findViewById(R.id.searchText);
                    searchText.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            performSearch(query);
                            return true;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {
                            performSearch(newText);
                            return true;
                        }
                    });

                } else {
                    // Handle the case where the user is not found
                    Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                // Handle the case where username is not passed correctly
                Toast.makeText(this, "Username not received.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // Handle the case where extras is null
            Toast.makeText(this, "No data received.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Called when the user returns from AddItemActivity, it will check the result code
     * and add the new item to the item list if the result code is RESULT_OK
     * onSuccess, the item will be added to the database, and the homepage will be updated
     * @param requestCode - the code that was passed to startActivityForResult
     * @param resultCode - the result code that was passed back from AddItemActivity
     * @param data - the intent that was passed back from AddItemActivity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       homePageAdapter.updateItems(userItemManager);

        // Check which request we're responding to
        if (requestCode == 1) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // Get the new item from the result intent
                Item newItem = (Item) data.getSerializableExtra("newItem");
                userItemManager.addItem_DataSync(newItem, user);
                // Add the new item to your item list
                user.setItemManager(userItemManager);

                // Update total value
                updateTotalValue();
                // Notify the adapter that the data set has changed
                homePageAdapter.notifyDataSetChanged();
            }
        }
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            // save the photo to the gallery
            PhotoManager photoManager = new PhotoManager(this);
            ImageView hiddenImage = findViewById(R.id.HomescreenHiddenImageView);
            hiddenImage.setImageBitmap(imageBitmap);
            photoManager.SaveImageToGallery(hiddenImage);
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Click Listener for grid, either delete or view item property
     * @param view - the view that was clicked
     * @param position - the position of the view in the grid
     */
    private void gridViewClickEvent(View view, int position) {
        currentItemManager = homePageAdapter.getItemList();

        if (deleteMode) { // if delete
            if (itemsToRemove.contains(userItemManager.getItem(position))) { // if already selected, unselect
                itemsToRemove.remove(userItemManager.getItem(position));
                homePageAdapter.notifyDataSetChanged();
                view.setBackgroundColor(Color.TRANSPARENT);
            } else {
                itemsToRemove.add(currentItemManager.getItem(position));
                view.setBackgroundColor(Color.LTGRAY); // change color if selected
            }
        } else { // if user wants to view property item
            Intent intent = new Intent(getApplicationContext(), ViewItemActivity.class);
            intent.putExtra("item", currentItemManager.getItem(position));
            intent.putExtra("image", R.drawable.app_icon);
            intent.putExtra("username", userName);
            startActivity(intent);
        }
    }

    /**
     * Click Listener for deleteButton
     *      1) First click is to activate delete mode
     *      2) Second click is to delete selected items
     */
    private void deleteButtonClickEvent() {
        currentItemManager = homePageAdapter.getItemList();
        if (!deleteMode) {
            deleteMode = true;
            Toast.makeText(HomePageActivity.this, "Select items to be deleted", Toast.LENGTH_SHORT).show();
            deleteButton.setBackgroundResource(R.drawable.gray_button);
        } else {
            // Delete selected items
            for (Item item : itemsToRemove) {
                currentItemManager.deleteItem(item);
                userItemManager.deleteItem(item);
                userItemManager.deleteItem_DataSync(item, user);
            }
            updateTotalValue();
            homePageAdapter.notifyDataSetChanged(); // Refresh the adapter
            itemsToRemove.clear(); // Clear the selection
            user.setItemManager(userItemManager);
            deleteMode = false; // Exit delete mode
            deleteButton.setBackgroundResource(R.drawable.white_button);
        }
    }

    /**
     * Performs the search and displays it based on given query
     * @param query: searched query
     */
    private void performSearch(String query) {
        ItemManager filteredItems = new ItemManager();

        // Goes through all items to see if they match query
        for (Item item: userItemManager.getAllItems()) {
            if (item.matchesQuery(query)) {
                filteredItems.addItem(item);
            }
        }

        // update the homePage
        homePageAdapter.updateItems(filteredItems);
        homePageAdapter.notifyDataSetChanged();
        updateTotalValue();
    }

    /**
     * Displays sortFragment (menu)
     */
    private void sortClickEvent() {
        SortOptions sortFragment = new SortOptions();
        sortFragment.show(getSupportFragmentManager(), "sortDialog");
    }

    private void takePictureClickEvent() {
        // Opens the camera on the phone
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
    }

    /**
     * Allows user to pick two dates and filter out items (startDate, endDate)
     * @param isStartDate
     */
    private void filterClickEvent(final boolean isStartDate) {
        if (isStartDate) {
            Toast.makeText(this, "Choose Start Date:", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this, "Choose End Date:", Toast.LENGTH_SHORT).show();
        }

        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                HomePageActivity.this,
                (view, year, monthOfYear, dayOfMonth) -> {
                    Calendar newDate = Calendar.getInstance();
                    newDate.set(year, monthOfYear, dayOfMonth);
                    if (isStartDate) {
                        startDate = newDate.getTime();
                        // Once start date is selected, show DatePickerDialog for the end date
                        filterClickEvent(false);
                    } else {
                        endDate = newDate.getTime();
                        // After end date is selected, filter the items
                        filterItemsByDateRange();
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    /**
     * Gets items that are dated between the given dates
     */
    private void filterItemsByDateRange() {
        if (startDate == null || endDate == null) {
            Toast.makeText(HomePageActivity.this, "Please select both start and end dates.", Toast.LENGTH_LONG).show();
            return;
        }

        ItemManager filteredItems = new ItemManager();
        for (Item item : userItemManager.getAllItems()) {
            if (!item.getPurchaseDate().before(startDate) && !item.getPurchaseDate().after(endDate)) {
                filteredItems.addItem(item);
            }
        }
        homePageAdapter.updateItems(filteredItems);
        homePageAdapter.notifyDataSetChanged();
        updateTotalValue();
    }

    /**
     * Gets total value of every item and display in homePage
     */
    private void updateTotalValue() {
        float totalValue = 0;
        ArrayList<Item> allItems = homePageAdapter.getItemList().getAllItems();
        for (Item item: allItems) {
            totalValue += item.getValue();
        }
        totalValueView.setText(String.format("Total Value: $%.2f", totalValue));
    }

    /**
     *
     * @param sortBy: what property is being used to sort the items
     * @param order: descending or ascending
     */
    @Override
    public void onSortOptionSelected(String sortBy, String order) {
        currentItemManager = homePageAdapter.getItemList();
        if (currentItemManager != null) {
            // Sort the items on the currentItemManager
            currentItemManager.sortItems(sortBy, order);

            // After sorting, notify the adapter that the underlying data has changed.
            homePageAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Logic for hiding the keyboard
     */
    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchText.getWindowToken(), 0);
    }

    /**
     * Hides keyboard if user clicks anywhere else on the screen
     * @param event The touch screen event.
     *
     * @return super.dispatchTouchEvent(event): boolean
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hideKeyboard();
        }
        return super.dispatchTouchEvent(event);
    }
}