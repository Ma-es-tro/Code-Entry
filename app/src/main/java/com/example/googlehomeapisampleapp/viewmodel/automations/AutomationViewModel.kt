
/* Copyright 2025 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.viewmodel.automations

import androidx.lifecycle.ViewModel
import com.google.home.automation.Action
import com.google.home.automation.Automation
import com.google.home.automation.Node
import com.google.home.automation.ParallelFlow
import com.google.home.automation.SelectFlow
import com.google.home.automation.SequentialFlow
import com.google.home.automation.Starter
import kotlinx.coroutines.flow.MutableStateFlow

class AutomationViewModel (var automation: Automation) : ViewModel() {

    val id: MutableStateFlow<String>
    val name: MutableStateFlow<String>
    val description: MutableStateFlow<String>
    val isActive: MutableStateFlow<Boolean>
    val isValid: MutableStateFlow<Boolean>
    val nodes: MutableStateFlow<List<Node>>

    val starters: MutableStateFlow<List<Starter>>
    val actions: MutableStateFlow<List<Action>>

    init {
        // Initialize attributes from the automation:
        id = MutableStateFlow(automation.id.id)
        name = MutableStateFlow(automation.name)
        description = MutableStateFlow(automation.description)
        isActive = MutableStateFlow(automation.isActive)
        isValid = MutableStateFlow(automation.isValid)
        nodes = MutableStateFlow(retrieveNodes(automation.automationGraph!!))
        // Initialize starters and actions by parsing nodes:
        starters = MutableStateFlow(retrieveStarters(nodes.value))
        actions = MutableStateFlow(retrieveActions(nodes.value))
    }

    fun retrieveNodes(node: Node) : List<Node> {
        // Container for all nodes discovered so far:
        val discoveredNodes: MutableList<Node> = mutableListOf(node)
        // Container for all child nodes to search:
        val childNodes: List<Node> =
            when (node) {
                is SequentialFlow -> node.nodes
                is ParallelFlow -> node.nodes
                is SelectFlow -> node.nodes
                else -> emptyList()
            }
        // Add the results from child nodes recursively:
        for (childNode in childNodes) {
            discoveredNodes.addAll(retrieveNodes(childNode))
        }
        // Return all discovered nodes:
        return discoveredNodes
    }

    fun retrieveStarters(nodes: List<Node>): List<Starter> {
        // Container for all starter nodes:
        val starterNodes: MutableList<Starter> = mutableListOf()
        // Extract nodes that are starters:
        for (node in nodes)
            if (node is Starter)
                starterNodes.add(node)
        // Return the starter nodes:
        return starterNodes
    }

    fun retrieveActions(nodes: List<Node>): List<Action> {
        // Container for all action nodes:
        val actionNodes: MutableList<Action> = mutableListOf()
        // Extract nodes that are actions:
        for (node in nodes)
            if (node is Action)
                actionNodes.add(node)
        // Return the action nodes:
        return actionNodes
    }

}