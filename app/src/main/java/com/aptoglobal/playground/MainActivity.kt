package com.aptoglobal.playground

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import com.aptoglobal.playground.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDrawerLayout()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeDrawerLayout() = binding.run {
        toggle = ActionBarDrawerToggle(this@MainActivity, drawerLayout, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.firstItem -> Toast.makeText(this@MainActivity, "First item", Toast.LENGTH_SHORT).show()
                R.id.secondItem -> Toast.makeText(this@MainActivity, "Second item", Toast.LENGTH_SHORT).show()
                R.id.thirdItem -> Toast.makeText(this@MainActivity, "Third item", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
