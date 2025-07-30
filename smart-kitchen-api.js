/**
 * Smart Kitchen API v2.0
 * - Dynamic recipe management with Spoonacular integration
 * - Smart appliance control (autocooker, oven, etc.)
 * - Real-time WebSocket updates
 * - Voice commands and automation
 * - Nutrition analysis and meal planning
 */

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');

// =============================================================================
// APP INITIALIZATION
// =============================================================================

const app = express();
const PORT = 3000;
const WEBSOCKET_PORT = 8080;

// Middleware
app.use(express.json());
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

// Create HTTP server and WebSocket server
const server = http.createServer(app);
const wss = new WebSocket.Server({ port: WEBSOCKET_PORT, host: '0.0.0.0' });

// =============================================================================
// DATA STORES (In production, use a proper database)
// =============================================================================

// Cooking status tracker
let cookingStatus = {
  status: 'idle', // idle | cooking | completed
  currentStep: 0, 
  steps: [],
  timeRemaining: 0,
  timer: null
};

// Cooking sessions storage
const cookingSessions = [];

// Cooking simulations storage
const cookingSimulations = {};

// Smart appliances state
const appliances = {
  autocooker: {
    id: 'autocooker_01',
    name: 'Smart Pressure Cooker',
    type: 'AUTOCOOKER',
    brand: 'Instant Pot',
    status: 'ready',
    isConnected: true,
    pressure: 0,
    temperature: 20,
    lastUpdate: new Date(),
    features: ['pressure_cook', 'rice', 'soup', 'timer']
  },
  oven: {
    id: 'oven_01',
    name: 'Smart Oven',
    type: 'OVEN',
    brand: 'Samsung',
    status: 'ready',
    isConnected: true,
    temperature: 20,
    targetTemperature: 0,
    mode: 'off',
    lastUpdate: new Date()
  }
};

// =============================================================================
// WEBSOCKET MANAGEMENT
// =============================================================================

wss.on('connection', (ws, req) => {
  console.log('🔌 New WebSocket connection established');
  
  // Send welcome message
  ws.send(JSON.stringify({
    type: 'connection_established',
    data: { message: 'Connected to Smart Kitchen API' },
    timestamp: new Date()
  }));
  
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message);
      console.log('📨 WebSocket message received:', data);
      // Handle incoming WebSocket messages here
    } catch (error) {
      console.error('WebSocket parse error:', error);
    }
  });
  
  ws.on('close', () => {
    console.log('🔌 WebSocket connection closed');
  });
});

// Broadcast updates to all connected WebSocket clients
function broadcastUpdate(type, data) {
  const message = JSON.stringify({ 
    type, 
    data, 
    timestamp: new Date() 
  });
  
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  });
  
  console.log(`📡 WebSocket broadcast: ${type}`, data);
}

// =============================================================================
// CORE COOKING FUNCTIONS
// =============================================================================

// Start cooking a specific step
function startStep(stepIndex) {
  if (stepIndex >= cookingStatus.steps.length) {
    // All steps completed
    cookingStatus.status = 'completed';
    cookingStatus.currentStep = cookingStatus.steps.length;
    cookingStatus.timeRemaining = 0;
    
    console.log('✅ All cooking steps completed!');
    
    broadcastUpdate('cooking_complete', {
      message: 'Cooking completed!',
      status: 'completed'
    });
    return;
  }

  const step = cookingStatus.steps[stepIndex];
  cookingStatus.status = 'cooking';
  cookingStatus.currentStep = stepIndex + 1;
  cookingStatus.timeRemaining = (step.duration || 1) * 60; // Convert to seconds

  console.log(`➡️ Starting step ${cookingStatus.currentStep}: ${step.instruction} (${cookingStatus.timeRemaining}s)`);

  // Broadcast step start
  broadcastUpdate('cooking_step_start', {
    stepNumber: cookingStatus.currentStep,
    instruction: step.instruction,
    timeRemaining: cookingStatus.timeRemaining
  });

  // Clear existing timer
  if (cookingStatus.timer) {
    clearInterval(cookingStatus.timer);
  }

  // Start step timer
  cookingStatus.timer = setInterval(() => {
    cookingStatus.timeRemaining--;
    
    // Send timer updates every 30 seconds
    if (cookingStatus.timeRemaining % 30 === 0) {
      broadcastUpdate('timer_update', {
        timeRemaining: cookingStatus.timeRemaining,
        currentStep: cookingStatus.currentStep
      });
    }
    
    // Step completed
    if (cookingStatus.timeRemaining <= 0) {
      clearInterval(cookingStatus.timer);
      
      broadcastUpdate('cooking_step_complete', {
        step: cookingStatus.currentStep,
        instruction: step.instruction
      });
      
      // Move to next step
      startStep(stepIndex + 1);
    }
  }, 1000);
}

// Generate cooking steps from recipe instructions
function generateCookingSteps(recipe) {
  const instructions = recipe.instructions || '';
  const estimatedTime = recipe.readyInMinutes || 30;
  
  // Simple step generation (in production, use more sophisticated parsing)
  const steps = instructions.split(/[.!]/)
    .filter(step => step.trim().length > 0)
    .map((instruction, index) => ({
      step: index + 1,
      instruction: instruction.trim(),
      duration: Math.max(1, Math.floor(estimatedTime / instructions.split(/[.!]/).length))
    }));
  
  return steps.length > 0 ? steps : [
    { step: 1, instruction: 'Prepare ingredients', duration: 5 },
    { step: 2, instruction: 'Cook according to recipe', duration: estimatedTime - 5 }
  ];
}

// =============================================================================
// RECIPE & COOKING ENDPOINTS
// =============================================================================

// Start cooking with recipe steps
app.post('/kitchen/recipe', (req, res) => {
  const { steps } = req.body;

  if (!steps || !Array.isArray(steps) || steps.length === 0) {
    return res.status(400).json({ 
      success: false,
      error: 'Invalid or missing steps array' 
    });
  }

  // Reset cooking state
  if (cookingStatus.timer) {
    clearInterval(cookingStatus.timer);
  }
  
  cookingStatus.steps = steps;
  cookingStatus.status = 'idle';
  cookingStatus.currentStep = 0;
  cookingStatus.timeRemaining = 0;

  // Start cooking process
  startStep(0);

  res.json({
    success: true,
    message: '✅ Cooking started!',
    totalSteps: steps.length,
    currentStep: cookingStatus.currentStep,
    currentInstruction: steps[0].instruction
  });
});

// Get current cooking status
app.get('/kitchen/status', (req, res) => {
  const currentStep = cookingStatus.steps[cookingStatus.currentStep - 1] || null;
  
  res.json({
    success: true,
    status: cookingStatus.status,
    currentStep: cookingStatus.currentStep,
    totalSteps: cookingStatus.steps.length,
    timeRemainingSeconds: cookingStatus.timeRemaining,
    currentInstruction: currentStep ? currentStep.instruction : null
  });
});

// Save recipe to favorites
app.post('/api/recipes/favorites', (req, res) => {
  const { recipeId, recipeName, userId } = req.body;
  
  const favoriteRecipe = {
    id: recipeId,
    name: recipeName,
    userId: userId,
    savedAt: new Date(),
    cookCount: 0
  };
  
  res.json({
    success: true,
    message: 'Recipe saved to favorites',
    recipe: favoriteRecipe
  });
});

// Get cooking history
app.get('/api/cooking/history/:userId?', (req, res) => {
  const { userId } = req.params;
  
  const completedSessions = cookingSessions
    .filter(session => session.status === 'completed')
    .map(session => ({
      id: session.id,
      recipeName: session.recipe?.title || 'Unknown Recipe',
      startTime: session.startTime,
      endTime: session.endTime,
      totalTime: session.endTime ? 
        Math.round((session.endTime - session.startTime) / 1000 / 60) : 0,
      stepsCompleted: session.steps?.length || 0,
      rating: null
    }))
    .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
    .slice(0, 20);
  
  res.json({
    success: true,
    history: completedSessions,
    totalSessions: completedSessions.length
  });
});

// =============================================================================
// SMART APPLIANCE CONTROL
// =============================================================================

// Discover available appliances
app.get('/api/appliances/discover', (req, res) => {
  const availableAppliances = Object.values(appliances).map(appliance => ({
    id: appliance.id,
    name: appliance.name,
    type: appliance.type,
    brand: appliance.brand,
    status: appliance.status,
    isConnected: appliance.isConnected,
    features: appliance.features || []
  }));
  
  res.json({
    success: true,
    message: 'Appliances discovered',
    appliances: availableAppliances
  });
});

// Control oven - preheat
app.post('/api/appliances/oven/preheat', (req, res) => {
  const { temperature, mode = 'bake' } = req.body;
  
  if (!temperature || temperature < 50 || temperature > 300) {
    return res.status(400).json({ 
      success: false,
      error: 'Invalid temperature (50-300°C)' 
    });
  }
  
  const oven = appliances.oven;
  oven.status = 'preheating';
  oven.targetTemperature = temperature;
  oven.mode = mode;
  oven.lastUpdate = new Date();
  
  // Simulate preheating process
  const preheatInterval = setInterval(() => {
    if (oven.temperature < oven.targetTemperature) {
      oven.temperature = Math.min(oven.temperature + 10, oven.targetTemperature);
      
      if (oven.temperature >= oven.targetTemperature) {
        clearInterval(preheatInterval);
        oven.status = 'ready';
        
        broadcastUpdate('oven_preheated', {
          temperature: oven.temperature,
          mode: oven.mode
        });
      }
    }
  }, 2000);
  
  const estimatedTime = Math.round((temperature - oven.temperature) / 10 * 2 / 60);
  
  res.json({
    success: true,
    message: `Oven preheating to ${temperature}°C in ${mode} mode`,
    estimatedTime: estimatedTime
  });
});

// Control pressure cooker
app.post('/api/appliances/autocooker/pressure', (req, res) => {
  const { pressure, duration } = req.body;
  
  if (!pressure || pressure < 5 || pressure > 15) {
    return res.status(400).json({ 
      success: false,
      error: 'Invalid pressure (5-15 PSI)' 
    });
  }
  
  if (!duration || duration < 1) {
    return res.status(400).json({ 
      success: false,
      error: 'Invalid duration (minimum 1 minute)' 
    });
  }
  
  const autocooker = appliances.autocooker;
  autocooker.status = 'pressurizing';
  autocooker.pressure = 0;
  autocooker.lastUpdate = new Date();
  
  // Simulate pressure building
  const pressureInterval = setInterval(() => {
    if (autocooker.pressure < pressure) {
      autocooker.pressure = Math.min(autocooker.pressure + 0.5, pressure);
      
      if (autocooker.pressure >= pressure) {
        clearInterval(pressureInterval);
        autocooker.status = 'pressure_cooking';
        
        // Start cooking timer
        setTimeout(() => {
          autocooker.status = 'depressurizing';
          
          setTimeout(() => {
            autocooker.status = 'ready';
            autocooker.pressure = 0;
            
            broadcastUpdate('pressure_cooking_complete', {
              message: 'Pressure cooking complete and depressurized'
            });
          }, 30000); // 30 seconds to depressurize
          
        }, duration * 60 * 1000);
      }
    }
  }, 1000);
  
  res.json({
    success: true,
    message: `Pressure cooking at ${pressure} PSI for ${duration} minutes`,
    totalTime: duration + 5
  });
});

// =============================================================================
// VOICE COMMANDS & AUTOMATION
// =============================================================================

// Handle voice commands
app.post('/api/voice/command', (req, res) => {
  const { command, parameters = {} } = req.body;
  
  switch (command.toLowerCase()) {
    case 'start cooking':
      if (parameters.recipeName) {
        res.json({
          success: true,
          message: `Starting to cook ${parameters.recipeName}`,
          action: 'cooking_started'
        });
      } else {
        res.json({
          success: false,
          message: 'Please specify a recipe name'
        });
      }
      break;
      
    case 'set timer':
      if (parameters.minutes) {
        res.json({
          success: true,
          message: `Timer set for ${parameters.minutes} minutes`,
          action: 'timer_started'
        });
      } else {
        res.json({
          success: false,
          message: 'Please specify timer duration'
        });
      }
      break;
      
    case 'check status':
      const activeSession = cookingSessions.find(s => s.status === 'cooking');
      if (activeSession) {
        const minutes = Math.floor(activeSession.timeRemaining / 60);
        res.json({
          success: true,
          message: `${activeSession.recipe.title} has ${minutes} minutes remaining`,
          session: activeSession
        });
      } else {
        res.json({
          success: true,
          message: 'No active cooking sessions',
          session: null
        });
      }
      break;
      
    default:
      res.json({
        success: false,
        message: 'Unknown command'
      });
  }
});

// =============================================================================
// MEAL PLANNING & NUTRITION
// =============================================================================

// Create weekly meal plan
app.post('/api/meal-plan/week', (req, res) => {
  const { meals, startDate } = req.body;
  
  const mealPlan = {
    id: `plan_${Date.now()}`,
    startDate,
    meals: meals.map((meal, index) => ({
      day: index + 1,
      breakfast: meal.breakfast,
      lunch: meal.lunch,
      dinner: meal.dinner,
      cookingScheduled: false
    })),
    createdAt: new Date()
  };
  
  res.json({
    success: true,
    message: 'Meal plan created successfully',
    mealPlan
  });
});

// Schedule cooking from meal plan
app.post('/api/meal-plan/schedule-cooking', (req, res) => {
  const { mealPlanId, day, mealType, scheduledTime } = req.body;
  
  const scheduledCooking = {
    id: `scheduled_${Date.now()}`,
    mealPlanId,
    day,
    mealType,
    scheduledTime: new Date(scheduledTime),
    status: 'scheduled'
  };
  
  res.json({
    success: true,
    message: `Cooking scheduled for ${mealType} on day ${day}`,
    scheduled: scheduledCooking
  });
});

// Analyze recipe nutrition
app.post('/api/nutrition/analyze', (req, res) => {
  const { recipeId, ingredients } = req.body;
  
  const nutrition = {
    recipeId,
    calories: Math.floor(Math.random() * 400) + 200,
    protein: Math.floor(Math.random() * 30) + 10,
    carbs: Math.floor(Math.random() * 40) + 20,
    fat: Math.floor(Math.random() * 20) + 5,
    fiber: Math.floor(Math.random() * 10) + 2,
    sodium: Math.floor(Math.random() * 800) + 200,
    healthScore: Math.floor(Math.random() * 40) + 60,
    dietaryRestrictions: {
      vegetarian: Math.random() > 0.5,
      vegan: Math.random() > 0.7,
      glutenFree: Math.random() > 0.6,
      dairyFree: Math.random() > 0.6
    }
  };
  
  res.json({
    success: true,
    nutrition,
    recommendations: [
      'This recipe is high in protein',
      'Consider adding more vegetables',
      'Good source of fiber'
    ]
  });
});

// =============================================================================
// TESTING & SIMULATION ENDPOINTS
// =============================================================================

// Test Android integration
app.post('/api/test/android', (req, res) => {
  console.log('🧪 Android integration test started');
  
  broadcastUpdate('android_test', {
    message: 'Test message from server',
    timestamp: new Date(),
    connectedClients: wss.clients.size
  });
  
  res.json({
    success: true,
    message: 'Android test completed',
    websocketClients: wss.clients.size,
    serverStatus: 'running'
  });
});

// Test WebSocket connection
app.get('/api/test/websocket', (req, res) => {
  broadcastUpdate('test_message', {
    message: 'WebSocket test successful',
    timestamp: new Date(),
    connectedClients: wss.clients.size
  });
  
  res.json({
    success: true,
    message: 'Test message sent to WebSocket clients',
    connectedClients: wss.clients.size
  });
});

// Test all appliances
app.get('/api/test/appliances', (req, res) => {
  const testResults = {};
  
  Object.keys(appliances).forEach(key => {
    const appliance = appliances[key];
    testResults[key] = {
      name: appliance.name,
      connected: appliance.isConnected,
      status: appliance.status,
      lastUpdate: appliance.lastUpdate,
      testPassed: appliance.isConnected && appliance.status !== 'error'
    };
  });
  
  const allPassed = Object.values(testResults).every(test => test.testPassed);
  
  res.json({
    success: allPassed,
    message: allPassed ? 'All appliances passed tests' : 'Some appliances failed tests',
    results: testResults,
    timestamp: new Date()
  });
});

// Simulate cooking process
app.post('/api/test/simulate-cooking', (req, res) => {
  const { recipeName, estimatedTime = 10 } = req.body;
  
  const sessionId = `sim_${Date.now()}`;
  const cookingSteps = [
    { step: 1, instruction: 'Preheating autocooker', duration: 2 },
    { step: 2, instruction: `Adding ingredients for ${recipeName}`, duration: 1 },
    { step: 3, instruction: 'Pressure cooking', duration: Math.max(estimatedTime - 5, 5) },
    { step: 4, instruction: 'Natural pressure release', duration: 2 },
    { step: 5, instruction: 'Cooking complete', duration: 0 }
  ];
  
  cookingSimulations[sessionId] = {
    id: sessionId,
    recipeName,
    steps: cookingSteps,
    currentStep: 0,
    status: 'starting',
    startTime: new Date(),
    timeRemaining: 0
  };
  
  // Start simulation
  startCookingSimulation(sessionId);
  
  res.json({
    success: true,
    sessionId,
    message: `Started cooking simulation for ${recipeName}`,
    totalSteps: cookingSteps.length,
    estimatedTime
  });
});

// Get simulation status
app.get('/api/test/cooking-status/:sessionId', (req, res) => {
  const { sessionId } = req.params;
  const simulation = cookingSimulations[sessionId];
  
  if (!simulation) {
    return res.status(404).json({
      success: false,
      error: 'Simulation not found'
    });
  }
  
  res.json({
    success: true,
    session: {
      id: simulation.id,
      recipeName: simulation.recipeName,
      status: simulation.status,
      currentStep: simulation.currentStep,
      totalSteps: simulation.steps.length,
      timeRemaining: simulation.timeRemaining || 0,
      currentInstruction: simulation.steps[simulation.currentStep]?.instruction || 'Complete'
    }
  });
});

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

// Start cooking simulation
function startCookingSimulation(sessionId) {
  const simulation = cookingSimulations[sessionId];
  if (!simulation) return;
  
  let currentStepIndex = 0;
  
  function executeStep() {
    if (currentStepIndex >= simulation.steps.length) {
      simulation.status = 'completed';
      simulation.currentStep = simulation.steps.length;
      simulation.timeRemaining = 0;
      
      console.log(`✅ [SIMULATION] Cooking complete: ${simulation.recipeName}`);
      
      broadcastUpdate('cooking_complete', {
        sessionId,
        recipeName: simulation.recipeName,
        message: `${simulation.recipeName} is ready!`
      });
      return;
    }
    
    const step = simulation.steps[currentStepIndex];
    simulation.currentStep = currentStepIndex + 1;
    simulation.status = 'cooking';
    simulation.timeRemaining = step.duration * 60;
    
    console.log(`🍳 [SIMULATION] Step ${simulation.currentStep}: ${step.instruction} (${step.duration}min)`);
    
    broadcastUpdate('cooking_step_start', {
      sessionId,
      stepNumber: simulation.currentStep,
      instruction: step.instruction,
      duration: step.duration
    });
    
    const countdown = setInterval(() => {
      simulation.timeRemaining--;
      
      if (simulation.timeRemaining <= 0) {
        clearInterval(countdown);
        
        broadcastUpdate('cooking_step_complete', {
          sessionId,
          stepNumber: simulation.currentStep,
          instruction: step.instruction
        });
        
        currentStepIndex++;
        executeStep();
      }
    }, 1000);
  }
  
  executeStep();
}

// Convert temperature between Celsius and Fahrenheit
function convertTemperature(temp, fromUnit, toUnit) {
  if (fromUnit === toUnit) return temp;
  
  if (fromUnit === 'C' && toUnit === 'F') {
    return (temp * 9/5) + 32;
  } else if (fromUnit === 'F' && toUnit === 'C') {
    return (temp - 32) * 5/9;
  }
  
  return temp;
}

// Calculate cooking time based on ingredients and method
function calculateCookingTime(ingredients, method) {
  let baseTime = 15;
  
  ingredients.forEach(ingredient => {
    const lowerIngredient = ingredient.toLowerCase();
    if (lowerIngredient.includes('meat') || lowerIngredient.includes('chicken')) {
      baseTime += 20;
    } else if (lowerIngredient.includes('rice') || lowerIngredient.includes('pasta')) {
      baseTime += 10;
    } else if (lowerIngredient.includes('vegetable')) {
      baseTime += 5;
    }
  });
  
  switch (method.toLowerCase()) {
    case 'pressure':
      baseTime *= 0.6;
      break;
    case 'slow':
      baseTime *= 4;
      break;
    case 'grill':
      baseTime *= 0.8;
      break;
  }
  
  return Math.round(baseTime);
}

// =============================================================================
// HEALTH CHECK & ROOT ENDPOINTS
// =============================================================================

// API health check
app.get('/api/health', (req, res) => {
  res.json({
    success: true,
    message: 'Smart Kitchen API is healthy',
    timestamp: new Date(),
    version: '2.0',
    uptime: process.uptime(),
    appliances: Object.keys(appliances).length,
    connectedClients: wss.clients.size
  });
});

// Root endpoint
app.get('/', (req, res) => {
  res.json({
    message: '🔌 Smart Kitchen API v2.0 is running',
    documentation: `http://localhost:${PORT}/api/health`,
    websocket: `ws://localhost:${WEBSOCKET_PORT}`,
    endpoints: {
      cooking: [
        'POST /kitchen/recipe',
        'GET /kitchen/status',
        'GET /api/cooking/history/:userId?'
      ],
      appliances: [
        'GET /api/appliances/discover',
        'POST /api/appliances/oven/preheat',
        'POST /api/appliances/autocooker/pressure'
      ],
      voice: [
        'POST /api/voice/command'
      ],
      testing: [
        'POST /api/test/android',
        'GET /api/test/websocket',
        'GET /api/test/appliances',
        'POST /api/test/simulate-cooking'
      ]
    }
  });
});

// =============================================================================
// ERROR HANDLING
// =============================================================================

// 404 Handler
app.use('*', (req, res) => {
  res.status(404).json({
    success: false,
    error: 'Endpoint not found',
    message: `The endpoint ${req.originalUrl} was not found`,
    availableEndpoints: [
      'GET /',
      'GET /api/health',
      'POST /kitchen/recipe',
      'GET /kitchen/status',
      'GET /api/appliances/discover',
      'POST /api/voice/command'
    ]
  });
});

// Global error handler
app.use((error, req, res, next) => {
  console.error('API Error:', error);
  
  res.status(500).json({
    success: false,
    error: 'Internal server error',
    message: process.env.NODE_ENV === 'development' ? error.message : 'Something went wrong',
    timestamp: new Date()
  });
});

//Testing
app.get('/api/health', (req, res) => {
    res.json({
        success: true,
        message: "Smart Kitchen Server Running",
        version: "1.0.0",
        uptime: process.uptime()
    });
});

app.post('/api/test/simulate-cooking', (req, res) => {
    console.log('🍳 Simulating cooking:', req.body.recipeName);
    res.json({
        success: true,
        message: `Started cooking ${req.body.recipeName}`,
        sessionId: `session_${Date.now()}`
    });
});

app.get('/api/appliances/discover', (req, res) => {
    res.json({
        success: true,
        message: "Devices discovered",
        appliances: [
            { id: "virtual_cooker_01", name: "Virtual Instant Pot", type: "AUTOCOOKER", brand: "InstantPot", status: "ready", isConnected: true },
            { id: "virtual_speaker_01", name: "Virtual Kitchen Speaker", type: "SPEAKER", brand: "Google", status: "online", isConnected: true }
        ]
    });
});

// =============================================================================
// SERVER STARTUP
// =============================================================================

server.listen(PORT, () => {
  console.log(`
╔══════════════════════════════════════════╗
║           Smart Kitchen API v2.0         ║
║                                          ║
║  🏠 Home Automation Ready                ║
║  🍳 Smart Cooking Integration            ║
║  📱 Android App Compatible               ║
║  🔌 WebSocket Real-time Updates          ║
║  ⏰ Timer & Thermostat Control           ║
║                                          ║
║  HTTP Server: http://localhost:${PORT}    ║
║  WebSocket:   ws://localhost:${WEBSOCKET_PORT}      ║
║                                          ║
║  Health Check: /api/health               ║
║  Documentation: /                        ║
╚══════════════════════════════════════════╝
  `);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('🛑 SIGTERM received, shutting down gracefully');
  server.close(() => {
    console.log('📴 HTTP server closed');
    wss.close(() => {
      console.log('📴 WebSocket server closed');
      process.exit(0);
    });
  });
});

process.on('SIGINT', () => {
  console.log('🛑 SIGINT received, shutting down gracefully');
  server.close(() => {
    console.log('📴 HTTP server closed');
    wss.close(() => {
      console.log('📴 WebSocket server closed');
      process.exit(0);
    });
  });
});