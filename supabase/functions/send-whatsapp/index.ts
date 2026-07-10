import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { phoneNumber, verificationCode } = await req.json()
    
    if (!phoneNumber || !verificationCode) {
      return new Response(JSON.stringify({ error: 'Missing phoneNumber or verificationCode' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400,
      })
    }

    // Clean phone number
    let cleanPhone = phoneNumber.replace('+', '').replace(/^00/, '');
    if (cleanPhone.startsWith('0')) {
      cleanPhone = '964' + cleanPhone.substring(1);
    }

    const instanceId = Deno.env.get('GREEN_INSTANCE_ID')
    const apiToken = Deno.env.get('GREEN_API_TOKEN')

    if (!instanceId || !apiToken) {
      return new Response(JSON.stringify({ error: 'Missing Green API credentials' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 500,
      })
    }

    const url = `https://api.green-api.com/waInstance${instanceId}/sendMessage/${apiToken}`
    
    const message = `رمز التحقق الخاص بك هو: ${verificationCode}`

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        chatId: `${cleanPhone}@c.us`,
        message: message,
      }),
    })

    const data = await response.json()

    if (!response.ok) {
        throw new Error(JSON.stringify(data))
    }

    return new Response(JSON.stringify(data), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
